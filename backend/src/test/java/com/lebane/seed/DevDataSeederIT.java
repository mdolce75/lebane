package com.lebane.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lebane.support.PostgresContainer;

/** El seed corre al arrancar (SEED_ENABLED=true) y es idempotente: ejecutarlo de nuevo no duplica nada. */
@SpringBootTest(properties = "lebane.seed.enabled=true")
@ImportTestcontainers(PostgresContainer.class)
class DevDataSeederIT {

    private static final String COUNT_SEED_DEPARTAMENTOS =
            "select count(*) from departamento where codigo like 'SEED-%'";
    private static final String COUNT_SEED_CONSULTAS = """
            select count(*) from consulta c join departamento d on d.id = c.departamento_id
            where d.codigo like 'SEED-%'""";

    @Autowired
    private DevDataSeeder seeder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsOnStartupAndIsIdempotent() {
        int expectedDepartamentos = SeedData.departamentos().size();
        int expectedConsultas = consultasEsperadas();
        assertThat(count(COUNT_SEED_DEPARTAMENTOS)).isEqualTo(expectedDepartamentos);
        assertThat(count(COUNT_SEED_CONSULTAS)).isEqualTo(expectedConsultas);

        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        assertThat(count(COUNT_SEED_DEPARTAMENTOS)).isEqualTo(expectedDepartamentos);
        assertThat(count(COUNT_SEED_CONSULTAS)).isEqualTo(expectedConsultas);
    }

    @Test
    void completaLasConsultasDeEjemploEnUnaBaseQueYaTeniaElSeed() {
        // Simula una base creada con el seed anterior: SEED-0001 sin las consultas de ejemplo adicionales.
        String deEjemplo = "select count(*) from consulta c join departamento d on d.id = c.departamento_id "
                + "where d.codigo = 'SEED-0001' and c.email like '%.s0001-%@example.com'";
        jdbcTemplate.update("delete from consulta where email like '%.s0001-%@example.com'");
        assertThat(count(deEjemplo)).isZero();

        seeder.run(new DefaultApplicationArguments());

        assertThat(count(deEjemplo)).isEqualTo(SeedConsultas.cantidadPara("SEED-0001"));
        assertThat(count(COUNT_SEED_CONSULTAS)).isEqualTo(consultasEsperadas());
    }

    @Test
    void noTocaConsultasRealesDeLosAvisosDelSeed() {
        jdbcTemplate.update("insert into consulta (id, departamento_id, nombre, email, mensaje, created_at) "
                + "select nextval('consulta_seq'), id, 'Cliente real', 'real@cliente.com', "
                + "'Consulta real de un cliente', now() from departamento where codigo = 'SEED-0002'");
        try {
            seeder.run(new DefaultApplicationArguments());

            assertThat(count("select count(*) from consulta where email = 'real@cliente.com'")).isEqualTo(1);
            assertThat(count(COUNT_SEED_CONSULTAS)).isEqualTo(consultasEsperadas() + 1);
        } finally {
            jdbcTemplate.update("delete from consulta where email = 'real@cliente.com'");
        }
    }

    @Test
    void losAvisosQueTerminanVendidosQuedanVendidos() {
        // Se crean DISPONIBLE (no se publica como vendido), reciben fotos y consultas y se venden al final del seed.
        SeedData.departamentos().forEach(seed -> assertThat(jdbcTemplate.queryForObject(
                "select estado from departamento where codigo = ?", String.class, seed.codigo()))
                .as(seed.codigo()).isEqualTo(seed.estadoFinal().name()));
        assertThat(SeedData.departamentos()).anyMatch(seed -> !seed.estadoFinal().admiteAlta());
    }

    @Test
    void existingSeedIsNotOverwritten() {
        jdbcTemplate.update("update departamento set titulo = 'Editado a mano' where codigo = 'SEED-0001'");

        seeder.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.queryForObject("select titulo from departamento where codigo = 'SEED-0001'",
                String.class)).isEqualTo("Editado a mano");
    }

    /** Las consultas originales de cada aviso del seed más las de ejemplo adicionales. */
    private static int consultasEsperadas() {
        return SeedData.departamentos().stream()
                .mapToInt(seed -> seed.consultas().size() + SeedConsultas.cantidadPara(seed.codigo()))
                .sum();
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
