package com.lebane.departamento.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado comercial de la publicación. */
@Schema(description = "Estado comercial. VENDIDO no recibe nuevas consultas.")
public enum EstadoDepartamento {
    DISPONIBLE,
    RESERVADO,
    VENDIDO;

    /** Un departamento vendido no recibe nuevas consultas. */
    public boolean aceptaConsultas() {
        return this != VENDIDO;
    }
}
