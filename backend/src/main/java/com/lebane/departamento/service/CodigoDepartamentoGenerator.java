package com.lebane.departamento.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Genera códigos comerciales {@code DEP-XXXXXXXX} (8 caracteres Crockford Base32: sin I, L, O ni U, legibles por
 * teléfono). 32^8 ≈ 1,1·10^12 combinaciones; el índice único de la base es la garantía final ante una colisión.
 */
@Component
public class CodigoDepartamentoGenerator {

    static final String PREFIX = "DEP-";
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder codigo = new StringBuilder(PREFIX.length() + LENGTH).append(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            codigo.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return codigo.toString();
    }
}
