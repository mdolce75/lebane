package com.lebane.departamento.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Consulta recibida por un departamento, para que el anunciante la lea y responda. */
@Schema(description = "Consulta recibida por un departamento")
public record ConsultaResponse(
        @Schema(description = "ID de la consulta", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "Nombre de quien consulta", example = "Ana Pérez", requiredMode = Schema.RequiredMode.REQUIRED)
        String nombre,
        @Schema(description = "Email de contacto", example = "ana@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Teléfono de contacto, si lo dejó", example = "+54 11 5555-1234",
                requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String telefono,
        @Schema(description = "Mensaje", example = "¿Se puede visitar el sábado por la mañana?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String mensaje,
        @Schema(description = "Fecha de la consulta (UTC)", example = "2026-10-01T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {

    @Override
    public String toString() {
        return "ConsultaResponse[id=" + id + ", datos personales omitidos]";
    }
}
