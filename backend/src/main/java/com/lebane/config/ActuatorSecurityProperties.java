package com.lebane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales para endpoints sensibles de Actuator (metrics, prometheus).
 * Provienen de ACTUATOR_USERNAME / ACTUATOR_PASSWORD; nunca se registran en logs.
 */
@Validated
@ConfigurationProperties(prefix = "lebane.security.actuator")
public record ActuatorSecurityProperties(@NotBlank String username, String password) {

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "ActuatorSecurityProperties[username=" + username + ", password=****]";
    }
}
