package com.lebane.departamento.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

/**
 * Tarjeta del listado: solo lo que muestra la pantalla. Sin descripción, dirección completa, coordenadas ni lista de
 * imágenes (eso está en el detalle).
 *
 * @param imagenPrincipalUrl URL de la foto de menor posición; {@code null} si no tiene fotos (el frontend muestra un
 *                           placeholder)
 */
public record DepartamentoListItemResponse(
        Long id,
        String codigo,
        String titulo,
        BigDecimal precio,
        Moneda moneda,
        int ambientes,
        int dormitorios,
        int banos,
        BigDecimal superficieM2,
        EstadoDepartamento estado,
        String ciudad,
        String provincia,
        String imagenPrincipalUrl,
        long cantidadImagenes,
        long cantidadConsultas,
        Instant createdAt) {
}
