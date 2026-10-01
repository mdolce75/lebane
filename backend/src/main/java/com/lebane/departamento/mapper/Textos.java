package com.lebane.departamento.mapper;

/** Normalización de textos de entrada antes de persistir. */
final class Textos {

    private Textos() {
    }

    /** Recorta espacios; para campos obligatorios (ya validados con {@code @NotBlank}). */
    static String requerido(String value) {
        return value == null ? null : value.strip();
    }

    /** Recorta espacios y convierte vacío en {@code null}: un opcional vacío no se guarda como "". */
    static String opcional(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
