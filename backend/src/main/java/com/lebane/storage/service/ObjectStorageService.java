package com.lebane.storage.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

import com.lebane.exception.DependencyUnavailableException;
import com.lebane.exception.ErrorCode;
import com.lebane.resilience.ResilientExecutor;
import com.lebane.resilience.TransientFailurePredicate;
import com.lebane.storage.client.MinioStorageClient;
import com.lebane.storage.config.StorageProperties;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Operaciones de storage con resiliencia (instancia {@code storage}: Retry + CircuitBreaker + TimeLimiter),
 * métricas ({@code lebane.storage.operations}, por operación y resultado) y logs estructurados.
 *
 * <p>Se registra: bucket, objectKey, tamaño, tipo, duración y resultado. Nunca credenciales, URLs firmadas ni el
 * contenido de los archivos. Un fallo transitorio se registra en WARN (dependencia degradada); uno permanente
 * (credenciales, permisos) en ERROR, porque requiere intervención.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);
    public static final String INSTANCE = "storage";
    public static final String PROVIDER = "minio";
    static final String METRIC = "lebane.storage.operations";

    private final MinioStorageClient client;
    private final ResilientExecutor resilience;
    private final StorageProperties properties;
    private final MeterRegistry meterRegistry;
    private final TransientFailurePredicate transientFailure = new TransientFailurePredicate();
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public ObjectStorageService(MinioStorageClient client, ResilientExecutor resilience,
            StorageProperties properties, MeterRegistry meterRegistry) {
        this.client = client;
        this.resilience = resilience;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public String bucket() {
        return properties.bucket();
    }

    /**
     * Garantiza que el bucket exista con lectura pública (idempotente). Se intenta al arrancar y, si MinIO no estaba
     * disponible, antes de la primera subida.
     */
    public void ensureBucket() {
        if (bucketReady.get() || !properties.createBucket()) {
            return;
        }
        run("ensureBucket", null, () -> {
            if (!client.bucketExists(properties.bucket())) {
                client.createBucket(properties.bucket());
                log.info("Storage bucket created", kv("bucket", properties.bucket()), kv("provider", PROVIDER));
            }
            client.allowPublicRead(properties.bucket());
            return null;
        });
        bucketReady.set(true);
    }

    /**
     * Sube un objeto. El contenido se reabre en cada intento ({@link InputStreamSource}): un reintento nunca envía un
     * stream ya consumido.
     */
    public void upload(String objectKey, InputStreamSource content, long sizeBytes, String contentType) {
        ensureBucket();
        log.info("Storage upload started", kv("bucket", properties.bucket()), kv("objectKey", objectKey),
                kv("sizeBytes", sizeBytes), kv("contentType", contentType));
        long start = System.nanoTime();
        run("upload", objectKey, () -> {
            try (InputStream stream = content.getInputStream()) {
                client.putObject(properties.bucket(), objectKey, stream, sizeBytes, contentType);
            }
            return null;
        });
        log.info("Storage upload completed", kv("bucket", properties.bucket()), kv("objectKey", objectKey),
                kv("sizeBytes", sizeBytes), kv("contentType", contentType), kv("durationMs", elapsedMs(start)));
    }

    public void delete(String objectKey) {
        run("delete", objectKey, () -> {
            client.removeObject(properties.bucket(), objectKey);
            return null;
        });
        log.info("Storage object deleted", kv("bucket", properties.bucket()), kv("objectKey", objectKey));
    }

    /**
     * Borrado compensatorio: deshace una subida cuya operación de negocio falló después. Nunca lanza excepción (no
     * debe ocultar el error original). Si falla, el objeto queda huérfano y se registra en ERROR con su clave para
     * poder limpiarlo.
     */
    public void deleteCompensating(String objectKey) {
        try {
            run("compensatingDelete", objectKey, () -> {
                client.removeObject(properties.bucket(), objectKey);
                return null;
            });
            log.warn("Storage compensating delete executed", kv("bucket", properties.bucket()),
                    kv("objectKey", objectKey), kv("compensation", true));
        } catch (RuntimeException e) {
            log.error("Storage compensating delete failed: orphan object", kv("bucket", properties.bucket()),
                    kv("objectKey", objectKey), kv("compensation", true), kv("cause", e.getClass().getSimpleName()));
        }
    }

    private <T> T run(String operation, String objectKey, java.util.concurrent.Callable<T> call) {
        long start = System.nanoTime();
        try {
            T result = resilience.execute(INSTANCE, PROVIDER, call);
            record(operation, "success", start);
            return result;
        } catch (Exception e) {
            record(operation, "failure", start);
            Throwable cause = e;
            boolean transientOrRejected = e instanceof CallNotPermittedException || transientFailure.test(e);
            Object[] fields = {kv("operation", operation), kv("bucket", properties.bucket()),
                    kv("objectKey", objectKey), kv("provider", PROVIDER), kv("circuitBreaker", INSTANCE),
                    kv("errorCode", ErrorCode.STORAGE_UNAVAILABLE), kv("cause", cause.getClass().getSimpleName()),
                    kv("durationMs", elapsedMs(start))};
            if (transientOrRejected) {
                log.warn("Storage operation failed", fields);
            } else {
                log.error("Storage operation failed (non-transient: check storage configuration)", fields);
            }
            throw new DependencyUnavailableException(ErrorCode.STORAGE_UNAVAILABLE,
                    "El servicio de imágenes no está disponible; intentá nuevamente en unos minutos", e);
        }
    }

    private void record(String operation, String outcome, long start) {
        Timer.builder(METRIC)
                .description("Operaciones contra el storage de imágenes")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .tag("provider", PROVIDER)
                .register(meterRegistry)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
