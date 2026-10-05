package com.lebane.departamento.repository;

import java.math.BigDecimal;
import java.util.Set;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

/**
 * Criterios del listado, ya validados y normalizados. Todos son opcionales ({@code null} o vacío = sin filtro) y se
 * combinan con AND.
 *
 * @param texto       subcadena del título (sin distinguir mayúsculas), 3+ caracteres
 * @param ciudad      ciudad exacta (sin distinguir mayúsculas)
 * @param estados     cualquiera de los estados indicados
 * @param moneda      moneda; obligatoria para filtrar por precio
 * @param conImagenes {@code true}: solo con fotos; {@code false}: solo sin fotos
 * @param dadosDeBaja {@code true}: solo los dados de baja; {@code false}: solo los publicados
 */
public record DepartamentoFiltro(
        String texto,
        String ciudad,
        Set<EstadoDepartamento> estados,
        Moneda moneda,
        BigDecimal precioMin,
        BigDecimal precioMax,
        Integer ambientesMin,
        Integer dormitoriosMin,
        Integer banosMin,
        BigDecimal superficieMin,
        BigDecimal superficieMax,
        Boolean conImagenes,
        boolean dadosDeBaja) {

    public static DepartamentoFiltro sinFiltros() {
        return new DepartamentoFiltro(null, null, Set.of(), null, null, null, null, null, null, null, null, null, false);
    }
}
