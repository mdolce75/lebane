package com.lebane.storage.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.ServerException;

/**
 * Operaciones crudas contra MinIO. Traduce las excepciones del SDK a {@link StorageTransientException} (se
 * reintenta) o {@link StorageClientException} (permanente). Sin reintentos, timeouts ni logs: eso lo agrega
 * {@code ObjectStorageService}.
 */
@Component
public class MinioStorageClient {

    /** Códigos S3 que indican un problema temporal del servidor. */
    private static final Set<String> TRANSIENT_CODES = Set.of("InternalError", "SlowDown", "ServiceUnavailable",
            "RequestTimeout", "XMinioServerNotInitialized", "XMinioStorageFull");

    private final MinioClient client;

    public MinioStorageClient(MinioClient client) {
        this.client = client;
    }

    public boolean bucketExists(String bucket) {
        return call(() -> client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
    }

    public void createBucket(String bucket) {
        call(() -> {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            return null;
        });
    }

    /** Lectura anónima de objetos (s3:GetObject); listar, escribir y borrar siguen requiriendo credenciales. */
    public void allowPublicRead(String bucket) {
        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket);
        call(() -> {
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            return null;
        });
    }

    public void putObject(String bucket, String objectKey, InputStream content, long size, String contentType) {
        call(() -> {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(content, size, -1).contentType(contentType).build());
            return null;
        });
    }

    /** Idempotente: borrar un objeto inexistente no es un error en S3. */
    public void removeObject(String bucket, String objectKey) {
        call(() -> {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return null;
        });
    }

    private <T> T call(MinioCall<T> operation) {
        try {
            return operation.call();
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : "ErrorResponse";
            if (TRANSIENT_CODES.contains(code)) {
                throw new StorageTransientException("Storage temporarily unavailable", code, e);
            }
            throw new StorageClientException("Storage rejected the operation", code, e);
        } catch (ServerException | InsufficientDataException | InternalException | IOException e) {
            throw new StorageTransientException("Storage temporarily unavailable", e.getClass().getSimpleName(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // InvalidKeyException, NoSuchAlgorithmException, XmlParserException, InvalidResponseException.
            throw new StorageClientException("Storage client error", e.getClass().getSimpleName(), e);
        }
    }

    @FunctionalInterface
    private interface MinioCall<T> {
        T call() throws Exception;
    }
}
