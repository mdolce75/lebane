package com.lebane.storage;

import java.time.Duration;

import org.springframework.util.unit.DataSize;

import com.lebane.storage.config.StorageProperties;

/** Propiedades de storage para tests unitarios (sin MinIO real). */
public final class TestStorageProperties {

    private TestStorageProperties() {
    }

    public static StorageProperties of(String publicUrl, String bucket) {
        return new StorageProperties("http://localhost:9000", publicUrl, "test-access", "test-secret", bucket,
                "us-east-1", true, DataSize.ofMegabytes(5), Duration.ofSeconds(1));
    }
}
