package com.lebane.departamento.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ListadoParamsValidationTest {

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
    void appliesDefaultsAndNormalizesText() {
        DepartamentoListadoParams params = new DepartamentoListadoParams("  balcón ", "   ", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, " ");

        assertThat(params.q()).isEqualTo("balcón");
        assertThat(params.ciudad()).isNull();
        assertThat(params.estado()).isEmpty();
        assertThat(params.pagina()).isZero();
        assertThat(params.cantidad()).isEqualTo(DepartamentoListadoParams.DEFAULT_SIZE);
        assertThat(params.sort()).isEqualTo(CampoOrden.DEFAULT);
        assertThat(validator.validate(params)).isEmpty();
    }

    @Test
    void acceptsAFullValidQuery() {
        DepartamentoListadoParams params = new DepartamentoListadoParams("balcón", "CABA",
                List.of(EstadoDepartamento.DISPONIBLE, EstadoDepartamento.RESERVADO), null, Moneda.USD,
                new BigDecimal("100000"), new BigDecimal("200000"), 2, 1, 1, new BigDecimal("40"),
                new BigDecimal("90"), true, null, 3, 50, "precio,desc");

        assertThat(validator.validate(params)).isEmpty();
    }

    @Test
    void rejectsOutOfRangeValues() {
        DepartamentoListadoParams params = new DepartamentoListadoParams("ab", null, null, null, null, null, null, 0, -1, 0,
                new BigDecimal("-1"), null, null, null, -1, 101, "titulo,asc");

        assertThat(errors(params)).containsOnlyKeys("q", "ambientesMin", "dormitoriosMin", "banosMin",
                "superficieMin", "pagina", "cantidad", "sort");
    }

    @Test
    void precioSinMonedaSeFiltraEnDolares() {
        DepartamentoListadoParams params = withPrecio(null, new BigDecimal("1000"), null);

        assertThat(validator.validate(params)).isEmpty();
        assertThat(params.moneda()).isEqualTo(Moneda.USD);
        assertThat(withPrecio(null, null, null).moneda()).isNull();
    }

    @Test
    void disponibleNoSeCombinaConEstado() {
        DepartamentoListadoParams ambos = new DepartamentoListadoParams(null, null,
                List.of(EstadoDepartamento.RESERVADO), true, null, null, null, null, null, null, null, null, null,
                null, null, null, null);

        assertThat(errors(ambos)).containsOnlyKeys("disponible");
    }

    @Test
    void rangesMustNotBeInverted() {
        DepartamentoListadoParams precio = withPrecio(Moneda.USD, new BigDecimal("200"), new BigDecimal("100"));
        DepartamentoListadoParams superficie = new DepartamentoListadoParams(null, null, null, null, null, null, null, null,
                null, null, new BigDecimal("90"), new BigDecimal("40"), null, null, null, null, null);

        assertThat(errors(precio)).containsOnlyKeys("precioMax");
        assertThat(errors(superficie)).containsOnlyKeys("superficieMax");
    }

    @Test
    void deepPagesBeyondTheResultWindowAreRejected() {
        DepartamentoListadoParams lastAllowed = page(99, 100);
        DepartamentoListadoParams tooDeep = page(100, 100);

        assertThat(validator.validate(lastAllowed)).isEmpty();
        assertThat(errors(tooDeep)).containsOnlyKeys("pagina");
    }

    private static DepartamentoListadoParams withPrecio(Moneda moneda, BigDecimal min, BigDecimal max) {
        return new DepartamentoListadoParams(null, null, null, null, moneda, min, max, null, null, null, null, null, null, null,
                null, null, null);
    }

    private static DepartamentoListadoParams page(int page, int size) {
        return new DepartamentoListadoParams(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                page, size, null);
    }

    private static Map<String, String> errors(DepartamentoListadoParams params) {
        return validator.validate(params).stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage, (a, b) -> a));
    }
}
