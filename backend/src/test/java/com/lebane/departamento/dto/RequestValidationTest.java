package com.lebane.departamento.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.byProvider(HibernateValidator.class).configure()
                .defaultLocale(Locale.forLanguageTag("es"))
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validDepartamentoHasNoViolations() {
        assertThat(validator.validate(TestFixtures.departamento())).isEmpty();
    }

    @Test
    void monoambienteWithoutBedroomsIsValid() {
        assertThat(validator.validate(TestFixtures.departamento(1, 0, null))).isEmpty();
    }

    @Test
    void reportsEachInvalidFieldByPath() {
        DepartamentoRequest invalid = new DepartamentoRequest(" ", null, new BigDecimal("-1"), null, 0, -1, 0,
                BigDecimal.ZERO, null,
                new DireccionRequest("", "", null, null, "", "", "C1414!", null, null, null));

        assertThat(fields(validator.validate(invalid))).containsExactlyInAnyOrder(
                "titulo", "precio", "moneda", "ambientes", "dormitorios", "banos", "superficieM2",
                "direccion.calle", "direccion.numero", "direccion.ciudad", "direccion.provincia",
                "direccion.codigoPostal");
    }

    @Test
    void precioRejectsMoreThanTwoDecimals() {
        DepartamentoRequest request = new DepartamentoRequest("Título", null, new BigDecimal("100.123"), Moneda.USD,
                2, 1, 1, new BigDecimal("40"), null, TestFixtures.direccion());

        assertThat(fields(validator.validate(request))).containsExactly("precio");
    }

    @Test
    void dormitoriosMustBeLessThanAmbientes() {
        Map<String, String> errors = messages(validator.validate(TestFixtures.departamento(2, 2, null)));

        assertThat(errors).containsOnlyKeys("dormitorios");
        assertThat(errors.get("dormitorios")).isEqualTo("debe ser menor que la cantidad de ambientes");
    }

    @Test
    void coordinatesMustComeTogether() {
        DireccionRequest soloLatitud = new DireccionRequest("Gorriti", "4850", null, null, "CABA", "CABA", null,
                new BigDecimal("-34.5"), null, null);

        assertThat(fields(validator.validate(soloLatitud))).containsExactly("longitud");
    }

    @Test
    void coordinatesOutOfRangeAreRejected() {
        DireccionRequest fueraDeRango = new DireccionRequest("Gorriti", "4850", null, null, "CABA", "CABA", null,
                new BigDecimal("-91"), new BigDecimal("181"), null);

        assertThat(fields(validator.validate(fueraDeRango))).containsExactlyInAnyOrder("latitud", "longitud");
    }

    @Test
    void validConsultaHasNoViolations() {
        assertThat(validator.validate(TestFixtures.consulta())).isEmpty();
    }

    @Test
    void consultaRejectsInvalidContactData() {
        ConsultaRequest invalid = new ConsultaRequest("", "no-es-un-email", "abc", "corto");

        assertThat(fields(validator.validate(invalid)))
                .containsExactlyInAnyOrder("nombre", "email", "telefono", "mensaje");
    }

    @Test
    void consultaToStringDoesNotExposePersonalData() {
        assertThat(TestFixtures.consulta().toString()).doesNotContain("Ana", "example.com", "5555");
    }

    @Test
    void estadoIsOptional() {
        assertThat(validator.validate(TestFixtures.departamento(3, 2, null))).isEmpty();
        assertThat(validator.validate(TestFixtures.departamento(3, 2, EstadoDepartamento.VENDIDO))).isEmpty();
    }

    private static <T> Set<String> fields(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static <T> Map<String, String> messages(Set<ConstraintViolation<T>> violations) {
        return violations.stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage, (a, b) -> a));
    }
}
