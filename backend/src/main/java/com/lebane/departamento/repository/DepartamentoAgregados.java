package com.lebane.departamento.repository;

/**
 * Datos calculados en PostgreSQL para un departamento del listado.
 *
 * @param imagenPrincipalKey clave del objeto de la imagen de menor posición; {@code null} si no tiene fotos
 */
public record DepartamentoAgregados(long cantidadImagenes, String imagenPrincipalKey, long cantidadConsultas) {

    public static final DepartamentoAgregados VACIO = new DepartamentoAgregados(0, null, 0);
}
