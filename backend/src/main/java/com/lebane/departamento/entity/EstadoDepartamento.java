package com.lebane.departamento.entity;

import java.util.EnumSet;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado comercial de la publicación y sus reglas de ciclo de vida:
 * <pre>
 *   DISPONIBLE ⇄ RESERVADO
 *       │           │
 *       └──► VENDIDO ◄┘   (final)
 * </pre>
 * Un departamento VENDIDO es un registro cerrado: no cambia de estado, no se edita, no cambia sus fotos y no recibe
 * consultas. Tampoco se puede publicar directamente como VENDIDO.
 */
@Schema(description = "Estado comercial. Transiciones: DISPONIBLE ⇄ RESERVADO y ambos → VENDIDO. VENDIDO es final: "
        + "no se edita, no cambia sus fotos ni recibe consultas, y no se puede publicar directamente como vendido.")
public enum EstadoDepartamento {
    DISPONIBLE,
    RESERVADO,
    VENDIDO;

    /** Estados a los que puede pasar desde el actual (sin contar quedarse en el mismo). */
    public Set<EstadoDepartamento> transicionesPermitidas() {
        return switch (this) {
            case DISPONIBLE -> EnumSet.of(RESERVADO, VENDIDO);
            case RESERVADO -> EnumSet.of(DISPONIBLE, VENDIDO);
            case VENDIDO -> EnumSet.noneOf(EstadoDepartamento.class);
        };
    }

    /** {@code true} si puede pasar a {@code destino}; quedarse en el mismo estado siempre está permitido. */
    public boolean puedeCambiarA(EstadoDepartamento destino) {
        return destino == this || transicionesPermitidas().contains(destino);
    }

    /** Un departamento vendido es un registro cerrado: no se editan sus datos ni sus fotos. */
    /** Estados de un aviso publicado (no vendido): los que cuentan para detectar avisos duplicados. */
    public static Set<EstadoDepartamento> activos() {
        return EnumSet.of(DISPONIBLE, RESERVADO);
    }

    public boolean esModificable() {
        return this != VENDIDO;
    }

    /** Estado válido para publicar un departamento nuevo (no se publica directamente como vendido). */
    public boolean admiteAlta() {
        return this != VENDIDO;
    }

    /** Un departamento vendido no recibe nuevas consultas. */
    public boolean aceptaConsultas() {
        return this != VENDIDO;
    }
}
