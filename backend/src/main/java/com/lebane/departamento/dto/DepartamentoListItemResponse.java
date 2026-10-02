package com.lebane.departamento.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tarjeta del listado: solo lo que muestra la pantalla. Sin descripción, dirección completa, coordenadas ni lista de
 * imágenes (eso está en el detalle).
 *
 * @param imagenPrincipalUrl URL de la foto de menor posición; {@code null} si no tiene fotos (el frontend muestra un
 *                           placeholder)
 */
@Schema(description = "Departamento en el listado: solo lo que muestra la tarjeta (sin descripción ni dirección completa).")
public record DepartamentoListItemResponse(
        @Schema(description = "ID interno", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "Código comercial legible, generado por el backend", example = "DEP-7K3M9QX2", requiredMode = Schema.RequiredMode.REQUIRED)
        String codigo,
        @Schema(description = "Título", example = "Luminoso 3 ambientes con balcón en Palermo", requiredMode = Schema.RequiredMode.REQUIRED)
        String titulo,
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
        @Schema(description = "Ciudad", example = "Ciudad Autónoma de Buenos Aires", requiredMode = Schema.RequiredMode.REQUIRED)
        String ciudad,
        @Schema(description = "Provincia", example = "CABA", requiredMode = Schema.RequiredMode.REQUIRED)
        String provincia,
        @Schema(description = "URL pública de la foto principal (la de menor posición); null si no tiene fotos", example = "http://localhost:9000/lebane-images/departamentos/1/a.png")
        String imagenPrincipalUrl,
        @Schema(description = "Cantidad de fotos", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        long cantidadImagenes,
        @Schema(description = "Cantidad de consultas recibidas", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        long cantidadConsultas,
        @Schema(description = "Fecha de alta (UTC)", example = "2026-10-01T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {
}
