package com.lebane.address.provider;

import java.util.List;

import com.lebane.address.dto.SugerenciaDireccion;

/**
 * Proveedor de autocompletado de direcciones. El dominio depende solo de esta abstracción; la implementación activa
 * se elige con {@code ADDRESS_PROVIDER} ({@code stub} | {@code external}).
 *
 * <p>Las implementaciones no aplican timeouts, reintentos ni fallbacks: eso lo hace el servicio con Resilience4j,
 * igual para todas.
 */
public interface AddressProvider {

    /** Nombre corto para logs, métricas y la respuesta ({@code georef}, {@code stub}). */
    String nombre();

    /**
     * @param texto  dirección parcial ingresada por el usuario (ya validada: 3..100 caracteres)
     * @param limite cantidad máxima de sugerencias
     */
    List<SugerenciaDireccion> buscar(String texto, int limite) throws Exception;
}
