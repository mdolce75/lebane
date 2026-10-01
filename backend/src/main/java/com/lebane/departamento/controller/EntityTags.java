package com.lebane.departamento.controller;

import java.util.HashSet;
import java.util.Set;

import com.lebane.exception.PreconditionFailedException;

/**
 * ETags fuertes derivados de la versión de concurrencia optimista: {@code "<version>"}.
 */
final class EntityTags {

    private EntityTags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Versiones aceptadas por un header {@code If-Match}. Vacío si el header falta o es {@code *} (sin precondición).
     * Un valor que no corresponde a ningún ETag emitido por la API nunca coincide: 412.
     */
    static Set<Long> parseIfMatch(String header) {
        if (header == null || header.isBlank() || header.strip().equals("*")) {
            return Set.of();
        }
        Set<Long> versions = new HashSet<>();
        for (String raw : header.split(",")) {
            String tag = raw.strip();
            if (tag.startsWith("W/")) {
                tag = tag.substring(2); // If-Match usa comparación fuerte, pero se toleran clientes que lo agregan
            }
            if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            try {
                versions.add(Long.parseLong(tag));
            } catch (NumberFormatException e) {
                // ETag ajeno a la API: no coincide con ninguna versión.
            }
        }
        if (versions.isEmpty()) {
            throw new PreconditionFailedException();
        }
        return versions;
    }
}
