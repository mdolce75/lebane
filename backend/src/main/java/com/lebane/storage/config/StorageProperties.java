package com.lebane.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuración pública del storage de imágenes (STORAGE_PUBLIC_URL, STORAGE_BUCKET). Las credenciales y el endpoint
 * interno se incorporan con la integración de MinIO (Fase 4).
 */
@Validated
@ConfigurationProperties(prefix = "lebane.storage")
public record StorageProperties(@NotBlank String publicUrl, @NotBlank String bucket) {
}
