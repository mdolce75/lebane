package com.lebane.departamento.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/** Confirmación de una consulta registrada. No devuelve los datos personales enviados. */
@Schema(description = "Consulta registrada. No devuelve los datos personales enviados.")
public record ConsultaCreatedResponse(
        @Schema(description = "ID de la consulta", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "ID del departamento consultado", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long departamentoId,
        @Schema(description = "Fecha de la consulta (UTC)", example = "2026-10-01T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {
}
