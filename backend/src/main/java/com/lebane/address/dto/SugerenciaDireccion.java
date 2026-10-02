package com.lebane.address.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dirección sugerida, con los mismos campos que el formulario de departamento para poder completarlo. Independiente
 * del proveedor: ningún tipo de un proveedor externo sale del paquete {@code address}.
 *
 * @param latitud     {@code null} si el proveedor no informa coordenadas (nunca se inventan)
 * @param placeId     identificador del proveedor, prefijado con su nombre (p. ej. {@code georef:...})
 * @param descripcion texto legible para mostrar en la lista de sugerencias
 */
@Schema(description = "Dirección sugerida, lista para completar el formulario.")
public record SugerenciaDireccion(
        @Schema(description = "Calle", example = "Av. Del Libertador", requiredMode = Schema.RequiredMode.REQUIRED)
        String calle,
        @Schema(description = "Altura, si la búsqueda la incluía", example = "4850")
        String numero,
        @Schema(description = "Ciudad o localidad", example = "Ciudad Autónoma de Buenos Aires", requiredMode = Schema.RequiredMode.REQUIRED)
        String ciudad,
        @Schema(description = "Provincia", example = "Ciudad Autónoma de Buenos Aires", requiredMode = Schema.RequiredMode.REQUIRED)
        String provincia,
        @Schema(description = "Latitud (WGS84)", example = "-34.590347")
        BigDecimal latitud,
        @Schema(description = "Longitud (WGS84)", example = "-58.429718")
        BigDecimal longitud,
        @Schema(description = "Identificador de la sugerencia en el proveedor", example = "georef:0209801005940:4850", requiredMode = Schema.RequiredMode.REQUIRED)
        String placeId,
        @Schema(description = "Texto para mostrar en la lista", example = "AV. DEL LIBERTADOR 4850, Comuna 14, CABA", requiredMode = Schema.RequiredMode.REQUIRED)
        String descripcion) {
}
