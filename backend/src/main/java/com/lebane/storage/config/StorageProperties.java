package com.lebane.storage.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Storage de imágenes (MinIO / S3 compatible). Todo proviene de variables de entorno ({@code STORAGE_*},
 * {@code UPLOAD_MAX_FILE_SIZE}).
 *
 * @param endpoint       URL interna con la que el backend habla con MinIO (p. ej. {@code http://minio:9000})
 * @param publicUrl      URL con la que el navegador descarga las imágenes (bucket con lectura pública)
 * @param createBucket   crear el bucket y su política de lectura pública si no existen
 * @param maxFileSize    tamaño máximo por imagen (igual al límite multipart)
 * @param connectTimeout timeout de conexión del cliente HTTP; el tiempo total de cada operación lo acota el
 *                       TimeLimiter de la instancia {@code storage}
 */
@Validated
@ConfigurationProperties(prefix = "lebane.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String publicUrl,
        String accessKey,
        String secretKey,
        @NotBlank String bucket,
        @NotBlank String region,
        boolean createBucket,
        @NotNull DataSize maxFileSize,
        @NotNull Duration connectTimeout) {

    @Override
    public String toString() {
        // Las credenciales nunca se imprimen (logs de arranque, actuator, excepciones de binding).
        return "StorageProperties[endpoint=" + endpoint + ", publicUrl=" + publicUrl + ", bucket=" + bucket
                + ", region=" + region + ", accessKey=****, secretKey=****, createBucket=" + createBucket
                + ", maxFileSize=" + maxFileSize + "]";
    }
}
