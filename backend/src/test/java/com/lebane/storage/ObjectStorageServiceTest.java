package com.lebane.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamSource;

import com.lebane.exception.DependencyUnavailableException;
import com.lebane.exception.ErrorCode;
import com.lebane.resilience.ResilientExecutor;
import com.lebane.resilience.TestResilience;
import com.lebane.storage.client.MinioStorageClient;
import com.lebane.storage.client.StorageClientException;
import com.lebane.storage.client.StorageTransientException;
import com.lebane.storage.service.ObjectStorageService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ObjectStorageServiceTest {

    private final MinioStorageClient client = mock(MinioStorageClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private ResilientExecutor resilience;
    private ObjectStorageService service;

    @BeforeEach
    void setUp() {
        resilience = TestResilience.executor();
        service = new ObjectStorageService(client, resilience, TestStorageProperties.of("http://cdn", "bucket"),
                meters);
        when(client.bucketExists("bucket")).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        resilience.destroy();
    }

    @Test
    void uploadEnsuresBucketOnceAndRecordsSuccess() {
        service.upload("departamentos/1/a.png", content(), 4, "image/png");
        service.upload("departamentos/1/b.png", content(), 4, "image/png");

        verify(client, times(1)).bucketExists("bucket");
        verify(client, times(1)).allowPublicRead("bucket");
        verify(client, times(2)).putObject(eq("bucket"), anyString(), any(), eq(4L), eq("image/png"));
        assertThat(meters.get("lebane.storage.operations").tag("operation", "upload").tag("outcome", "success")
                .timer().count()).isEqualTo(2);
    }

    @Test
    void createsMissingBucket() {
        when(client.bucketExists("bucket")).thenReturn(false);

        service.ensureBucket();

        verify(client).createBucket("bucket");
        verify(client).allowPublicRead("bucket");
    }

    /** Cada intento reabre el stream: un reintento nunca envía un stream ya consumido. */
    @Test
    void retriesTransientFailuresReopeningTheContent() {
        AtomicInteger opened = new AtomicInteger();
        InputStreamSource source = () -> {
            opened.incrementAndGet();
            return new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
        };
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            InputStream stream = invocation.getArgument(2);
            assertThat(stream.readAllBytes()).hasSize(4);
            if (attempts.incrementAndGet() < 3) {
                throw new StorageTransientException("unavailable", "IOException", new IOException());
            }
            return null;
        }).when(client).putObject(anyString(), anyString(), any(), anyLong(), anyString());

        service.upload("departamentos/1/a.png", source, 4, "image/png");

        assertThat(attempts).hasValue(3);
        assertThat(opened).hasValue(3);
    }

    @Test
    void persistentTransientFailureBecomesStorageUnavailable() {
        doThrow(new StorageTransientException("unavailable", "ServerException", null))
                .when(client).putObject(anyString(), anyString(), any(), anyLong(), anyString());

        assertThatThrownBy(() -> service.upload("departamentos/1/a.png", content(), 4, "image/png"))
                .isInstanceOfSatisfying(DependencyUnavailableException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STORAGE_UNAVAILABLE);
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getMessage()).doesNotContain("minio", "bucket", "http");
                });
        verify(client, times(3)).putObject(anyString(), anyString(), any(), anyLong(), anyString());
        assertThat(meters.get("lebane.storage.operations").tag("outcome", "failure").timer().count()).isEqualTo(1);
    }

    @Test
    void permanentFailureIsNotRetried() {
        doThrow(new StorageClientException("rejected", "AccessDenied", null))
                .when(client).putObject(anyString(), anyString(), any(), anyLong(), anyString());

        assertThatThrownBy(() -> service.upload("departamentos/1/a.png", content(), 4, "image/png"))
                .isInstanceOf(DependencyUnavailableException.class);
        verify(client, times(1)).putObject(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    void compensatingDeleteNeverThrows() {
        doThrow(new StorageTransientException("unavailable", "IOException", null))
                .when(client).removeObject(anyString(), anyString());

        service.deleteCompensating("departamentos/1/a.png");

        verify(client, times(3)).removeObject("bucket", "departamentos/1/a.png");
    }

    @Test
    void bucketCreationCanBeDisabled() {
        ObjectStorageService withoutCreation = new ObjectStorageService(client, resilience,
                new com.lebane.storage.config.StorageProperties("http://minio", "http://cdn", "a", "s", "bucket",
                        "us-east-1", false, org.springframework.util.unit.DataSize.ofMegabytes(5),
                        java.time.Duration.ofSeconds(1)),
                meters);

        withoutCreation.ensureBucket();

        verify(client, never()).bucketExists(anyString());
    }

    private static InputStreamSource content() {
        return () -> new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
    }
}
