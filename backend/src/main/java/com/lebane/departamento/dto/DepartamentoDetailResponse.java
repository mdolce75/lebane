package com.lebane.departamento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalle completo de un departamento. {@code version} es la versión de concurrencia optimista: también viaja en el
 * header {@code ETag} y se devuelve en {@code If-Match} al editar.
 */
@Schema(description = "Departamento completo. La versión también viaja en el header ETag para editar con If-Match.")
public record DepartamentoDetailResponse(
        @Schema(description = "ID interno", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "Código comercial legible, generado por el backend", example = "DEP-7K3M9QX2", requiredMode = Schema.RequiredMode.REQUIRED)
        String codigo,
        @Schema(description = "Título", example = "Luminoso 3 ambientes con balcón en Palermo", requiredMode = Schema.RequiredMode.REQUIRED)
        String titulo,
        @Schema(description = "Descripción", example = "Frente, piso alto, cocina integrada.")
        String descripcion,
        @Schema(description = "Precio", example = "185000", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal precio,
        @Schema(description = "Moneda del precio", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
        Moneda moneda,
        @Schema(description = "Ambientes", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int ambientes,
        @Schema(description = "Dormitorios", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int dormitorios,
        @Schema(description = "Baños", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int banos,
        @Schema(description = "Superficie en m²", example = "72.5", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal superficieM2,
        @Schema(description = "Estado comercial", example = "DISPONIBLE", requiredMode = Schema.RequiredMode.REQUIRED)
        EstadoDepartamento estado,
        @Schema(description = "Dirección", requiredMode = Schema.RequiredMode.REQUIRED)
        DireccionResponse direccion,
        @Schema(description = "Fotos, ordenadas por posición (la primera es la principal)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ImagenResponse> imagenes,
        @Schema(description = "Cantidad de consultas recibidas", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        long cantidadConsultas,
        @Schema(description = "Versión para concurrencia optimista (la misma que el ETag)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @Schema(description = "Fecha de alta (UTC)", example = "2026-10-01T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Última modificación (UTC)", example = "2026-10-01T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt) {
}
