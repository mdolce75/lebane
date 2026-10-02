package com.lebane.address.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado del autocompletado.
 *
 * @param proveedor proveedor que respondió ({@code georef}, {@code stub})
 * @param degradado {@code true} si el proveedor no estuvo disponible: {@code sugerencias} viene vacío y el usuario
 *                  debe cargar la dirección a mano. Nunca se devuelven datos de reemplazo como si fueran reales.
 * @param mensaje   explicación para el usuario cuando {@code degradado} es {@code true}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado del autocompletado. Si el proveedor no está disponible: degradado = true, sin sugerencias y con un mensaje para cargar la dirección a mano.")
public record AutocompleteResponse(
        @Schema(description = "Sugerencias, de la más a la menos relevante", requiredMode = Schema.RequiredMode.REQUIRED)
        List<SugerenciaDireccion> sugerencias,
        @Schema(description = "Proveedor usado: stub (catálogo local) o georef (API externa)", example = "georef", requiredMode = Schema.RequiredMode.REQUIRED)
        String proveedor,
        @Schema(description = "true si el proveedor falló o su circuito está abierto", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean degradado,
        @Schema(description = "Mensaje para el usuario cuando degradado = true; se omite si no", example = "El autocompletado de direcciones no está disponible en este momento; ingresá la dirección manualmente.")
        String mensaje) {

    public static AutocompleteResponse ok(List<SugerenciaDireccion> sugerencias, String proveedor) {
        return new AutocompleteResponse(sugerencias, proveedor, false, null);
    }

    public static AutocompleteResponse degradado(String proveedor) {
        return new AutocompleteResponse(List.of(), proveedor, true,
                "El autocompletado de direcciones no está disponible en este momento; ingresá la dirección manualmente.");
    }
}
