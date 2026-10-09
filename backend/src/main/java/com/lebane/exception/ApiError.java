package com.lebane.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Esquema único de error de la API. Nunca contiene stack traces, SQL ni detalles de infraestructura.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Error de la API. Mismo esquema para toda respuesta 4xx/5xx; nunca contiene stack traces, SQL "
        + "ni detalles de infraestructura.")
public record ApiError(
        @Schema(description = "Momento del error (UTC)", example = "2026-10-01T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp,
        @Schema(description = "Status HTTP", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(description = "Código estable del error, para que el cliente decida qué hacer sin depender del texto",
                implementation = ErrorCode.class, requiredMode = Schema.RequiredMode.REQUIRED)
        String error,
        @Schema(description = "Mensaje apto para mostrar al usuario", example = "La solicitud contiene datos inválidos")
        String message,
        @Schema(description = "Path del request (sin query string)", example = "/api/departamentos")
        String path,
        @Schema(description = "Correlation ID del request: permite encontrar el detalle en los logs",
                example = "7f3c2a9e-req-42")
        String requestId,
        @Schema(description = "Errores por campo (solo en errores de validación). La clave es la ruta del campo en el "
                + "body o el nombre del parámetro; se omite si no hay.",
                example = "{\"direccion.ciudad\": \"no debe estar vacío\"}")
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, ErrorCode code, String message, String path, String requestId) {
        return of(status, code, message, path, requestId, Map.of());
    }

    public static ApiError of(int status, ErrorCode code, String message, String path, String requestId,
            Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code.name(), message, path, requestId, Map.copyOf(fieldErrors));
    }
}
