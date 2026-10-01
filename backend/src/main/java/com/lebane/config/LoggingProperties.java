package com.lebane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Configuración de logging (LOG_FORMAT, LOGSTASH_*). La consume logback-spring.xml mediante {@code <springProperty>};
 * esta clase existe para validar los valores al arrancar: logback-spring.xml elige los archivos incluidos a partir de
 * ellos ({@code logback-format-${LOG_FORMAT}.xml}, {@code logback-logstash-${LOGSTASH_ENABLED}.xml}), por lo que un
 * valor inválido dejaría la aplicación sin logs o con Logstash deshabilitado en silencio.
 */
@Validated
@ConfigurationProperties(prefix = "lebane.logging")
public record LoggingProperties(
        @NotNull @Pattern(regexp = "json|text", message = "LOG_FORMAT debe ser 'json' o 'text'") String format,
        @NotBlank String serviceName,
        @NotBlank String environment,
        @NotNull @Valid Logstash logstash) {

    public record Logstash(
            @NotNull @Pattern(regexp = "true|false", message = "LOGSTASH_ENABLED debe ser 'true' o 'false'") String enabled,
            @NotBlank String host,
            @Min(1) @Max(65535) int port) {
    }
}
