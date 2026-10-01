package com.lebane.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Esquema único de error de la API. Nunca contiene stack traces, SQL ni detalles de infraestructura.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, ErrorCode code, String message, String path, String requestId) {
        return of(status, code, message, path, requestId, Map.of());
    }

    public static ApiError of(int status, ErrorCode code, String message, String path, String requestId,
            Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code.name(), message, path, requestId, Map.copyOf(fieldErrors));
    }
}
