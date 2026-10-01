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
        int expectedConsultas = SeedData.departamentos().stream().mapToInt(seed -> seed.consultas().size()).sum();
        assertThat(count(COUNT_SEED_DEPARTAMENTOS)).isEqualTo(expectedDepartamentos);
        assertThat(count(COUNT_SEED_CONSULTAS)).isEqualTo(expectedConsultas);

        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        assertThat(count(COUNT_SEED_DEPARTAMENTOS)).isEqualTo(expectedDepartamentos);
        assertThat(count(COUNT_SEED_CONSULTAS)).isEqualTo(expectedConsultas);
    }

    @Test
    void existingSeedIsNotOverwritten() {
        jdbcTemplate.update("update departamento set titulo = 'Editado a mano' where codigo = 'SEED-0001'");

        seeder.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.queryForObject("select titulo from departamento where codigo = 'SEED-0001'",
                String.class)).isEqualTo("Editado a mano");
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
