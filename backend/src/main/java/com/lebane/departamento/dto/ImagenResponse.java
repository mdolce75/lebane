package com.lebane.departamento.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Foto de un departamento. {@code url} la resuelve el storage; la clave interna del objeto no se expone. */
@Schema(description = "Foto de un departamento.")
public record ImagenResponse(
        @Schema(description = "ID de la foto", example = "19", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "URL pública (lectura directa desde MinIO)", example = "http://localhost:9000/lebane-images/departamentos/1/80d7c1e2.png", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(description = "Tipo real, detectado por el contenido del archivo", example = "image/png", requiredMode = Schema.RequiredMode.REQUIRED)
        String contentType,
        @Schema(description = "Tamaño en bytes", example = "204800", requiredMode = Schema.RequiredMode.REQUIRED)
        long sizeBytes,
        @Schema(description = "Posición (0 = principal)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int posicion) {
}
