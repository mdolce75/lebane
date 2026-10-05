package com.lebane.departamento.repository;

import java.math.BigDecimal;
import java.time.Instant;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

/**
 * Proyección de una fila del listado: solo las columnas que muestra la pantalla, construida directamente en la
 * consulta ({@code select new ...}). No se carga la entidad ni se toca el contexto de persistencia.
 */
public record DepartamentoListadoRow(
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
        Instant createdAt,
        Instant fechaBaja) {
}
