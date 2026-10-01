package com.lebane.storage.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;

@Configuration(proxyBeanMethods = false)
public class MinioConfig {

    /**
     * Cliente MinIO. No conecta al crearse: la aplicación arranca aunque MinIO no esté disponible. Los timeouts de
     * lectura/escritura del cliente son amplios; el límite efectivo por operación lo impone el TimeLimiter.
     */
    @Bean
    MinioClient minioClient(StorageProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .retryOnConnectionFailure(false) // los reintentos los controla Resilience4j
                .build();
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.endpoint())
                .region(properties.region())
                .httpClient(httpClient);
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            builder.credentials(properties.accessKey(), properties.secretKey());
        }
        return builder.build();
    }
}
