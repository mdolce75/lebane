package com.lebane.address.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resultado del autocompletado.
 *
 * @param proveedor proveedor que respondió ({@code georef}, {@code stub})
 * @param degradado {@code true} si el proveedor no estuvo disponible: {@code sugerencias} viene vacío y el usuario
 *                  debe cargar la dirección a mano. Nunca se devuelven datos de reemplazo como si fueran reales.
 * @param mensaje   explicación para el usuario cuando {@code degradado} es {@code true}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutocompleteResponse(
        List<SugerenciaDireccion> sugerencias,
        String proveedor,
        boolean degradado,
        String mensaje) {

    public static AutocompleteResponse ok(List<SugerenciaDireccion> sugerencias, String proveedor) {
        return new AutocompleteResponse(sugerencias, proveedor, false, null);
    }

    public static AutocompleteResponse degradado(String proveedor) {
        return new AutocompleteResponse(List.of(), proveedor, true,
                "El autocompletado de direcciones no está disponible en este momento; ingresá la dirección manualmente.");
    }
}
