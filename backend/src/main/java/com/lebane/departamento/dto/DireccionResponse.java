package com.lebane.departamento.dto;

import java.math.BigDecimal;

public record DireccionResponse(
        String calle,
        String numero,
        String piso,
        String unidad,
        String ciudad,
        String provincia,
        String codigoPostal,
        BigDecimal latitud,
        BigDecimal longitud,
        String placeId) {
}
