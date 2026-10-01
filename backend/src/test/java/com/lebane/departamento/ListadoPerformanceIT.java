package com.lebane.departamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.web.PagedModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;
import com.lebane.departamento.service.DepartamentoListadoService;

import jakarta.persistence.EntityManagerFactory;

/**
 * Performance del listado con volumen realista (100k departamentos, ~200k imágenes, ~300k consultas) en un
 * PostgreSQL propio (no el compartido: el volumen no afecta a otros tests).
 *
 * <p>Para cada escenario se verifica, con los contadores del propio PostgreSQL ({@code pg_stat_user_tables}), que
 * ninguna de las consultas ejecutadas (página, COUNT y agregados) hizo un <em>sequential scan</em> sobre
 * {@code departamento}, {@code imagen} o {@code consulta}, y con las estadísticas de Hibernate, que la cantidad de
 * sentencias es fija (sin N+1).
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Testcontainers
class ListadoPerformanceIT {

    private static final Logger log = LoggerFactory.getLogger(ListadoPerformanceIT.class);
    private static final List<String> TABLAS = List.of("departamento", "imagen", "consulta");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static boolean datosCargados;

    @Autowired
    private DepartamentoListadoService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cargarVolumen() throws Exception {
        if (datosCargados) {
            return;
        }
        String script = new ClassPathResource("perf/datos-volumen.sql").getContentAsString(StandardCharsets.UTF_8);
        long start = System.nanoTime();
        jdbcTemplate.execute(script);
        // Estadísticas del planificador y mapa de visibilidad al día, como en una base en producción.
        jdbcTemplate.execute("VACUUM ANALYZE departamento, imagen, consulta");
        log.info("Volumen cargado: {} departamentos, {} imágenes, {} consultas en {} ms",
                count("departamento"), count("imagen"), count("consulta"), (System.nanoTime() - start) / 1_000_000);
        datosCargados = true;
    }

    @Test
    void defaultListingFirstPage() {
        sinFullScans("orden por defecto, página 0", params().build());
    }

    @Test
    void defaultListingDeepPage() {
        sinFullScans("orden por defecto, página 99 de 100", params().page(99, 100).build());
    }

    @Test
    void filterByEstado() {
        sinFullScans("estado=DISPONIBLE", params().estado(EstadoDepartamento.DISPONIBLE).build());
        sinFullScans("estado=VENDIDO", params().estado(EstadoDepartamento.VENDIDO).build());
    }

    @Test
    void filterByCiudad() {
        sinFullScans("ciudad=rosario", params().ciudad("rosario").build());
    }

    @Test
    void filterByPrecioSortedByPrecio() {
        sinFullScans("USD 100k-200k, orden precio", params().moneda(Moneda.USD)
                .precio("100000", "200000").sort("precio,asc").build());
        sinFullScans("orden precio desc", params().sort("precio,desc").build());
    }

    @Test
    void textSearch() {
        sinFullScans("q=balcón", params().q("balcón").build());
        sinFullScans("q=reciclado + ciudad", params().q("reciclado").ciudad("Córdoba").build());
    }

    @Test
    void sortBySuperficie() {
        sinFullScans("orden superficie desc", params().sort("superficieM2,desc").build());
    }

    @Test
    void combinedFilters() {
        sinFullScans("combinado", params().estado(EstadoDepartamento.DISPONIBLE).ciudad("Mendoza")
                .conImagenes(true).ambientesMin(3).build());
    }

    /**
     * Control negativo: la medición tiene que detectar un sequential scan real (filtro sobre una columna sin
     * índice). Si este test fallara, los escenarios anteriores no probarían nada.
     */
    @Test
    void measurementDetectsSequentialScans() {
        Map<String, long[]> antes = scans();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT count(*) FROM departamento WHERE calle = 'no existe'", Long.class);
            jdbcTemplate.execute("SELECT pg_stat_force_next_flush()");
        });
        Map<String, long[]> despues = scans();

        assertThat(despues.get("departamento")[0] - antes.get("departamento")[0]).isPositive();
    }

    // ---------- Soporte ----------

    /**
     * Ejecuta el listado en una transacción y fuerza el envío de las estadísticas de esa conexión al terminar
     * ({@code pg_stat_force_next_flush}); compara los contadores de scans antes y después.
     */
    private void sinFullScans(String escenario, DepartamentoListadoParams params) {
        Map<String, long[]> antes = scans();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        long start = System.nanoTime();
        PagedModel<DepartamentoListItemResponse> page = transactionTemplate.execute(status -> {
            PagedModel<DepartamentoListItemResponse> result = service.listar(params);
            jdbcTemplate.execute("SELECT pg_stat_force_next_flush()");
            return result;
        });
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        long statements = statistics.getPrepareStatementCount();
        Map<String, long[]> despues = scans();

        log.info("Escenario [{}]: {} filas, total {}, {} sentencias, {} ms, seq/idx scans {}", escenario,
                page.getContent().size(), page.getMetadata().totalElements(), statements, durationMs,
                TABLAS.stream().map(t -> t + "=" + (despues.get(t)[0] - antes.get(t)[0]) + "/"
                        + (despues.get(t)[1] - antes.get(t)[1])).collect(Collectors.joining(", ")));

        assertThat(page.getContent()).as(escenario).isNotEmpty();
        assertThat(statements).as("%s: sentencias (página + count + agregados)", escenario).isLessThanOrEqualTo(3);
        for (String tabla : TABLAS) {
            assertThat(despues.get(tabla)[0] - antes.get(tabla)[0])
                    .as("%s: sequential scans sobre %s", escenario, tabla).isZero();
        }
        assertThat(despues.get("departamento")[1] - antes.get("departamento")[1])
                .as("%s: el listado usó índices de departamento", escenario).isPositive();
    }

    /** {tabla -> [seq_scan, idx_scan]} vistos por PostgreSQL. */
    private Map<String, long[]> scans() {
        jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
        return jdbcTemplate.query(
                "SELECT relname, seq_scan, COALESCE(idx_scan, 0) AS idx_scan FROM pg_stat_user_tables "
                        + "WHERE relname IN ('departamento', 'imagen', 'consulta')",
                rs -> {
                    Map<String, long[]> map = new java.util.HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getString(1), new long[] {rs.getLong(2), rs.getLong(3)});
                    }
                    return map;
                });
    }

    private long count(String tabla) {
        Long value = jdbcTemplate.queryForObject("SELECT reltuples::bigint FROM pg_class WHERE relname = ?",
                Long.class, tabla);
        return value == null ? 0 : value;
    }

    private static ParamsBuilder params() {
        return new ParamsBuilder();
    }

    private static final class ParamsBuilder {
        private String q;
        private String ciudad;
        private List<EstadoDepartamento> estado;
        private Moneda moneda;
        private BigDecimal precioMin;
        private BigDecimal precioMax;
        private Integer ambientesMin;
        private Boolean conImagenes;
        private Integer page;
        private Integer size;
        private String sort;

        ParamsBuilder q(String value) {
            q = value;
            return this;
        }

        ParamsBuilder ciudad(String value) {
            ciudad = value;
            return this;
        }

        ParamsBuilder estado(EstadoDepartamento value) {
            estado = List.of(value);
            return this;
        }

        ParamsBuilder moneda(Moneda value) {
            moneda = value;
            return this;
        }

        ParamsBuilder precio(String min, String max) {
            precioMin = new BigDecimal(min);
            precioMax = new BigDecimal(max);
            return this;
        }

        ParamsBuilder ambientesMin(int value) {
            ambientesMin = value;
            return this;
        }

        ParamsBuilder conImagenes(boolean value) {
            conImagenes = value;
            return this;
        }

        ParamsBuilder page(int number, int pageSize) {
            page = number;
            size = pageSize;
            return this;
        }

        ParamsBuilder sort(String value) {
            sort = value;
            return this;
        }

        DepartamentoListadoParams build() {
            return new DepartamentoListadoParams(q, ciudad, estado, moneda, precioMin, precioMax, ambientesMin, null,
                    null, null, null, conImagenes, page, size, sort);
        }
    }
}
