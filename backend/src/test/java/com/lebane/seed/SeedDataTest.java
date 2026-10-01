package com.lebane.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lebane.seed.SeedData.SeedDepartamento;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** El seed no pasa por la API: se verifica que cumpla las mismas validaciones que una solicitud real. */
class SeedDataTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void everySeedPassesApiValidation() {
        for (SeedDepartamento seed : SeedData.departamentos()) {
            assertThat(validator.validate(seed.datos())).as(seed.codigo()).isEmpty();
            seed.consultas().forEach(consulta ->
                    assertThat(validator.validate(consulta)).as(seed.codigo()).isEmpty());
        }
    }

    @Test
    void seedCodesAreUniqueAndFitTheColumn() {
        List<String> codigos = SeedData.departamentos().stream().map(SeedDepartamento::codigo).toList();

        assertThat(codigos).doesNotHaveDuplicates().allSatisfy(codigo ->
                assertThat(codigo).matches("^SEED-\\d{4}$"));
    }
}
