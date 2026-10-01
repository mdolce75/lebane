package com.lebane.departamento.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;

/**
 * Órdenes admitidos por el listado (lista blanca: ningún nombre de propiedad del cliente llega a la consulta). Cada
 * uno se resuelve con un índice cuyo prefijo coincide con el ORDER BY y termina en {@code id} para que la paginación
 * sea determinística aunque haya valores repetidos.
 */
public enum CampoOrden {

    /** Más recientes primero por defecto. Índice {@code (created_at, id)}. */
    CREATED_AT("createdAt", List.of("createdAt", "id")),
    /** Precio dentro de cada moneda (ARS y USD no son comparables). Índice {@code (moneda, precio, id)}. */
    PRECIO("precio", List.of("moneda", "precio", "id")),
    /** Índice {@code (superficie_m2, id)}. */
    SUPERFICIE("superficieM2", List.of("superficieM2", "id"));

    public static final String DEFAULT = "createdAt,desc";
    /** Formato del parámetro {@code sort}: {@code campo[,asc|desc]}. */
    public static final String PATTERN = "^(createdAt|precio|superficieM2)(,(asc|desc|ASC|DESC))?$";

    private final String parametro;
    private final List<String> propiedades;

    CampoOrden(String parametro, List<String> propiedades) {
        this.parametro = parametro;
        this.propiedades = propiedades;
    }

    /**
     * Traduce {@code sort} (ya validado contra {@link #PATTERN}) a un {@link Sort} con todas las columnas del índice
     * en la misma dirección: así PostgreSQL puede recorrer el índice en un solo sentido.
     */
    public static Sort toSort(String sort) {
        String[] partes = sort.split(",");
        CampoOrden campo = Arrays.stream(values()).filter(c -> c.parametro.equals(partes[0])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Orden no admitido"));
        Sort.Direction direccion = partes.length > 1
                ? Sort.Direction.fromString(partes[1].toLowerCase(Locale.ROOT))
                : (campo == CREATED_AT ? Sort.Direction.DESC : Sort.Direction.ASC);
        return Sort.by(direccion, campo.propiedades.toArray(String[]::new));
    }
}
