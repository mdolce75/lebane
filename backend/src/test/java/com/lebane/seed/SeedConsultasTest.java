package com.lebane.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.seed.SeedData.SeedDepartamento;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class SeedConsultasTest {

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
    void cadaAvisoRecibeEntreTresYOchoConsultasSiempreIguales() {
        for (SeedDepartamento seed : SeedData.departamentos()) {
            List<ConsultaRequest> consultas = SeedConsultas.para(seed.codigo());
            assertThat(consultas).as(seed.codigo())
                    .hasSizeBetween(SeedConsultas.MINIMO, SeedConsultas.MAXIMO)
                    .hasSize(SeedConsultas.cantidadPara(seed.codigo()))
                    .isEqualTo(SeedConsultas.para(seed.codigo()));
        }
    }

    @Test
    void todasPasanLaValidacionDeLaApi() {
        for (SeedDepartamento seed : SeedData.departamentos()) {
            SeedConsultas.para(seed.codigo()).forEach(consulta ->
                    assertThat(validator.validate(consulta)).as(seed.codigo() + " " + consulta.email()).isEmpty());
        }
    }

    @Test
    void losEmailsSonFicticiosYUnicosIncluidasLasConsultasOriginalesDelSeed() {
        Set<String> emails = new HashSet<>();
        for (SeedDepartamento seed : SeedData.departamentos()) {
            seed.consultas().forEach(consulta -> assertThat(emails.add(seed.codigo() + consulta.email())).isTrue());
            SeedConsultas.para(seed.codigo()).forEach(consulta -> {
                assertThat(consulta.email()).endsWith("@example.com").matches("^[a-z.]+\\.s\\d{4}-\\d{2}@example\\.com$");
                assertThat(emails.add(seed.codigo() + consulta.email())).as(consulta.email()).isTrue();
            });
        }
    }

    @Test
    void hayVariedadEntreAvisos() {
        Set<Integer> cantidades = new HashSet<>();
        Set<String> primerosMensajes = new HashSet<>();
        for (SeedDepartamento seed : SeedData.departamentos()) {
            cantidades.add(SeedConsultas.cantidadPara(seed.codigo()));
            primerosMensajes.add(SeedConsultas.para(seed.codigo()).getFirst().mensaje());
        }
        assertThat(cantidades).hasSizeGreaterThan(3);
        // Hay 16 mensajes de ejemplo: con 500 avisos se repiten, pero el primero no es siempre el mismo.
        assertThat(primerosMensajes).hasSizeGreaterThan(10);
    }
}
