package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CodigoDepartamentoGeneratorTest {

    private final CodigoDepartamentoGenerator generator = new CodigoDepartamentoGenerator();

    @Test
    void generatesReadableCodesThatFitTheColumn() {
        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String codigo = generator.generate();
            assertThat(codigo).matches("^DEP-[0-9ABCDEFGHJKMNPQRSTVWXYZ]{8}$").hasSizeLessThanOrEqualTo(20);
            codigos.add(codigo);
        }
        assertThat(codigos).hasSize(1_000);
    }
}
