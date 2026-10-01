package com.lebane.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orígenes permitidos para CORS (CORS_ALLOWED_ORIGINS, separados por coma).
 * En Docker el frontend accede vía proxy de nginx (mismo origen), por lo que CORS solo aplica en desarrollo.
 */
@ConfigurationProperties(prefix = "lebane.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
    }
}
