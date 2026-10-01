package com.lebane.departamento.entity;

/** Estado comercial de la publicación. */
public enum EstadoDepartamento {
    DISPONIBLE,
    RESERVADO,
    VENDIDO;

    /** Un departamento vendido no recibe nuevas consultas. */
    public boolean aceptaConsultas() {
        return this != VENDIDO;
    }
}
