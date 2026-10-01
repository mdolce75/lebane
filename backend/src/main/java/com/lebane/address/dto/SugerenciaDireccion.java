package com.lebane.address.dto;

import java.math.BigDecimal;

/**
 * Dirección sugerida, con los mismos campos que el formulario de departamento para poder completarlo. Independiente
 * del proveedor: ningún tipo de un proveedor externo sale del paquete {@code address}.
 *
 * @param latitud     {@code null} si el proveedor no informa coordenadas (nunca se inventan)
 * @param placeId     identificador del proveedor, prefijado con su nombre (p. ej. {@code georef:...})
 * @param descripcion texto legible para mostrar en la lista de sugerencias
 */
public record SugerenciaDireccion(
        String calle,
        String numero,
        String ciudad,
        String provincia,
        BigDecimal latitud,
        BigDecimal longitud,
        String placeId,
        String descripcion) {
}
