package com.lebane.address.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Proveedor de direcciones ({@code ADDRESS_PROVIDER*}).
 *
 * @param provider   {@code stub} (local, sin red) o {@code external} (API Georef u otra compatible)
 * @param url        URL base del proveedor externo
 * @param apiKey     credencial opcional; se envía como {@code Authorization: Bearer} y nunca se registra
 * @param timeout    timeout de conexión/lectura del cliente HTTP (el TimeLimiter usa el mismo valor)
 * @param maxResults máximo de sugerencias por consulta
 */
@Validated
@ConfigurationProperties(prefix = "lebane.address")
public record AddressProperties(
        @NotNull @Pattern(regexp = "stub|external", message = "ADDRESS_PROVIDER debe ser 'stub' o 'external'")
        String provider,
        String url,
        String apiKey,
        @NotNull Duration timeout,
        @Min(1) @Max(10) int maxResults) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "AddressProperties[provider=" + provider + ", url=" + url + ", apiKey=****, timeout=" + timeout
                + ", maxResults=" + maxResults + "]";
    }
}
