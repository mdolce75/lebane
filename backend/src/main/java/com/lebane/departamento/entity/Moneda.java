package com.lebane.departamento.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Moneda en la que se publica el precio. */
@Schema(description = "Moneda del precio: pesos argentinos o dólares.")
public enum Moneda {
    ARS,
    USD
}
