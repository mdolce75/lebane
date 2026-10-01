package com.lebane.departamento.dto;

import java.time.Instant;

/** Confirmación de una consulta registrada. No devuelve los datos personales enviados. */
public record ConsultaCreatedResponse(Long id, Long departamentoId, Instant createdAt) {
}
