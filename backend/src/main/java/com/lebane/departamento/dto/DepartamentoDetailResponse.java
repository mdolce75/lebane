package com.lebane.departamento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

/**
 * Detalle completo de un departamento. {@code version} es la versión de concurrencia optimista: también viaja en el
 * header {@code ETag} y se devuelve en {@code If-Match} al editar.
 */
public record DepartamentoDetailResponse(
        Long id,
        String codigo,
        String titulo,
        String descripcion,
        BigDecimal precio,
        Moneda moneda,
        int ambientes,
        int dormitorios,
        int banos,
        BigDecimal superficieM2,
        EstadoDepartamento estado,
        DireccionResponse direccion,
        List<ImagenResponse> imagenes,
        long cantidadConsultas,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
