package com.lebane.departamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.support.TransactionTemplate;

import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Moneda;
import com.lebane.departamento.mapper.ConsultaMapper;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.departamento.service.DepartamentoListadoService;
import com.lebane.support.PostgresContainer;

import jakarta.persistence.EntityManagerFactory;

/**
 * Listado contra PostgreSQL real: filtros, orden, paginación y agregados calculados en la base. La base es
 * compartida con otros tests: cada test crea sus datos en una ciudad única y filtra por ella.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@ImportTestcontainers(PostgresContainer.class)
class ListadoIT {

    @Autowired
    private DepartamentoListadoService service;
    @Autowired
    private DepartamentoRepository departamentoRepository;
    @Autowired
    private ImagenRepository imagenRepository;
    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private DepartamentoMapper mapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String ciudad;

    @BeforeEach
    void setUp() {
        ciudad = "Ciudad IT " + UUID.randomUUID();
    }

    @Test
    void computesMainImageAndCountersInTheDatabase() {
        // Fotos insertadas desordenadas: la principal es la de menor posición, no la primera insertada.
        long conFotos = crear("Con fotos", "100000", Moneda.USD, 3, "70", EstadoDepartamento.DISPONIBLE,
                new int[] {3, 1, 4}, 6);
        long sinFotos = crear("Sin fotos", "100000", Moneda.USD, 3, "70", EstadoDepartamento.DISPONIBLE,
                new int[] {}, 0);

        List<DepartamentoListItemResponse> items = listar(params().build()).getContent();

        DepartamentoListItemResponse con = item(items, conFotos);
        assertThat(con.cantidadImagenes()).isEqualTo(3);
        assertThat(con.cantidadConsultas()).isEqualTo(6);
        assertThat(con.imagenPrincipalUrl()).endsWith("/" + conFotos + "/foto-1.jpg");
        DepartamentoListItemResponse sin = item(items, sinFotos);
        assertThat(sin.cantidadImagenes()).isZero();
        assertThat(sin.cantidadConsultas()).isZero();
        assertThat(sin.imagenPrincipalUrl()).isNull();
    }

    @Test
    void filtersAreAppliedInTheDatabase() {
        long a = crear("Luminoso con balcón", "150000", Moneda.USD, 3, "75", EstadoDepartamento.DISPONIBLE,
                new int[] {0}, 0);
        long b = crear("Monoambiente 50% financiado", "60000", Moneda.USD, 1, "30", EstadoDepartamento.RESERVADO,
                new int[] {}, 0);
        long c = crear("Casa con balcón y patio", "90000000", Moneda.ARS, 4, "120", EstadoDepartamento.VENDIDO,
                new int[] {0, 1}, 0);

        assertThat(ids(params().q("BALCÓN").build())).containsExactlyInAnyOrder(a, c);
        assertThat(ids(params().q("50%").build())).containsExactly(b);
        assertThat(ids(params().q("0% fin").build())).containsExactly(b);
        assertThat(ids(params().q("___").build())).isEmpty();
        assertThat(ids(params().estado(EstadoDepartamento.DISPONIBLE, EstadoDepartamento.RESERVADO).build()))
                .containsExactlyInAnyOrder(a, b);
        assertThat(ids(params().moneda(Moneda.USD).precio("100000", null).build())).containsExactly(a);
        assertThat(ids(params().moneda(Moneda.ARS).precio(null, "100000000").build())).containsExactly(c);
        assertThat(ids(params().ambientesMin(3).build())).containsExactlyInAnyOrder(a, c);
        assertThat(ids(params().superficie("40", "100").build())).containsExactly(a);
        assertThat(ids(params().conImagenes(true).build())).containsExactlyInAnyOrder(a, c);
        assertThat(ids(params().conImagenes(false).build())).containsExactly(b);
    }

    @Test
    void losDadosDeBajaSoloAparecenConSuFiltro() {
        long vigente = crear("Vigente", "100", Moneda.USD, 2, "40", EstadoDepartamento.DISPONIBLE, new int[] {}, 0);
        long baja = crear("De baja", "100", Moneda.USD, 2, "40", EstadoDepartamento.VENDIDO, new int[] {0}, 1);
        transactionTemplate.executeWithoutResult(status -> departamentoRepository.findById(baja).orElseThrow()
                .darDeBaja(Instant.parse("2026-10-05T12:00:00Z")));

        assertThat(ids(params().build())).containsExactly(vigente);
        assertThat(ids(params().dadosDeBaja().build())).containsExactly(baja);
        assertThat(ids(params().dadosDeBaja().estado(EstadoDepartamento.DISPONIBLE).build())).isEmpty();
    }

    @Test
    void cityFilterIgnoresCase() {
        long a = crear("Uno", "100", Moneda.USD, 2, "40", EstadoDepartamento.DISPONIBLE, new int[] {}, 0);

        assertThat(ids(new DepartamentoListadoParams(null, ciudad.toUpperCase(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null))).containsExactly(a);
    }

    @Test
    void sortsAndPaginatesDeterministically() {
        long barato = crear("A", "100000", Moneda.USD, 2, "50", EstadoDepartamento.DISPONIBLE, new int[] {}, 0);
        long caro = crear("B", "300000", Moneda.USD, 2, "40", EstadoDepartamento.DISPONIBLE, new int[] {}, 0);
        long medio = crear("C", "200000", Moneda.USD, 2, "90", EstadoDepartamento.DISPONIBLE, new int[] {}, 0);
        long pesos = crear("D", "1000", Moneda.ARS, 2, "60", EstadoDepartamento.DISPONIBLE, new int[] {}, 0);

        // Por defecto: más recientes primero.
        assertThat(ids(params().build())).containsExactly(pesos, medio, caro, barato);
        // Precio: dentro de cada moneda (ARS < USD alfabéticamente).
        assertThat(ids(params().sort("precio,asc").build())).containsExactly(pesos, barato, medio, caro);
        assertThat(ids(params().sort("superficieM2,desc").build())).containsExactly(medio, pesos, barato, caro);

        PagedModel<DepartamentoListItemResponse> pagina2 = listar(params().sort("precio,asc").page(1, 3).build());
        assertThat(pagina2.getContent()).extracting(DepartamentoListItemResponse::id).containsExactly(caro);
        assertThat(pagina2.getMetadata().totalElements()).isEqualTo(4);
        assertThat(pagina2.getMetadata().totalPages()).isEqualTo(2);

        assertThat(listar(params().page(5, 3).build()).getContent()).isEmpty();
    }

    /**
     * Cantidad fija de sentencias por página, sin importar cuántas filas, fotos o consultas haya: página + COUNT +
     * agregados. Sin cargas perezosas (entity fetches) ni N+1.
     */
    @Test
    void runsAConstantNumberOfStatements() {
        for (int i = 0; i < 7; i++) {
            crear("Depto " + i, "100000", Moneda.USD, 3, "70", EstadoDepartamento.DISPONIBLE, new int[] {0, 1, 2},
                    i);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        PagedModel<DepartamentoListItemResponse> fullPage = listar(params().page(0, 5).build());
        long fullPageStatements = statistics.getPrepareStatementCount();
        long fullPageEntityLoads = statistics.getEntityLoadCount();

        statistics.clear();
        listar(params().page(0, 50).build());
        long partialPageStatements = statistics.getPrepareStatementCount();

        assertThat(fullPage.getContent()).hasSize(5);
        assertThat(fullPage.getMetadata().totalElements()).isEqualTo(7);
        assertThat(fullPageStatements).as("página + count + agregados").isEqualTo(3);
        assertThat(partialPageStatements).as("página + agregados (el total se deduce)").isEqualTo(2);
        assertThat(fullPageEntityLoads).as("proyección: no se cargan entidades").isZero();
    }

    // ---------- Soporte ----------

    private long crear(String titulo, String precio, Moneda moneda, int ambientes, String superficie,
            EstadoDepartamento estado, int[] posicionesFotos, int consultas) {
        return transactionTemplate.execute(status -> {
            DepartamentoRequest request = new DepartamentoRequest(titulo, null, new BigDecimal(precio), moneda,
                    ambientes, ambientes - 1, 1, new BigDecimal(superficie), estado,
                    new DireccionRequest("Calle", "1", null, null, ciudad, "Provincia", null, null, null, null));
            Departamento departamento = departamentoRepository.saveAndFlush(
                    mapper.toNewEntity(request, "IT-" + UUID.randomUUID().toString().substring(0, 12)));
            for (int posicion : posicionesFotos) {
                imagenRepository.save(new Imagen(departamento,
                        "departamentos/" + departamento.getId() + "/foto-" + posicion + ".jpg", "image/jpeg", 100,
                        posicion));
            }
            ConsultaMapper consultaMapper = new ConsultaMapper();
            for (int i = 0; i < consultas; i++) {
                consultaRepository.save(consultaMapper.toEntity(departamento, TestFixtures.consulta()));
            }
            return departamento.getId();
        });
    }

    private PagedModel<DepartamentoListItemResponse> listar(DepartamentoListadoParams params) {
        return service.listar(params);
    }

    private List<Long> ids(DepartamentoListadoParams params) {
        return listar(params).getContent().stream().map(DepartamentoListItemResponse::id).toList();
    }

    private static DepartamentoListItemResponse item(List<DepartamentoListItemResponse> items, long id) {
        return items.stream().filter(i -> i.id() == id).findFirst().orElseThrow();
    }

    private ParamsBuilder params() {
        return new ParamsBuilder(ciudad);
    }

    /** Arma parámetros siempre filtrados por la ciudad del test. */
    private static final class ParamsBuilder {
        private final String ciudad;
        private String q;
        private List<EstadoDepartamento> estado;
        private Moneda moneda;
        private BigDecimal precioMin;
        private BigDecimal precioMax;
        private Integer ambientesMin;
        private BigDecimal superficieMin;
        private BigDecimal superficieMax;
        private Boolean dadosDeBaja;
        private Boolean conImagenes;
        private Integer page;
        private Integer size;
        private String sort;

        ParamsBuilder(String ciudad) {
            this.ciudad = ciudad;
        }

        ParamsBuilder q(String value) {
            q = value;
            return this;
        }

        ParamsBuilder estado(EstadoDepartamento... values) {
            estado = List.of(values);
            return this;
        }

        ParamsBuilder moneda(Moneda value) {
            moneda = value;
            return this;
        }

        ParamsBuilder precio(String min, String max) {
            precioMin = min == null ? null : new BigDecimal(min);
            precioMax = max == null ? null : new BigDecimal(max);
            return this;
        }

        ParamsBuilder ambientesMin(int value) {
            ambientesMin = value;
            return this;
        }

        ParamsBuilder superficie(String min, String max) {
            superficieMin = new BigDecimal(min);
            superficieMax = new BigDecimal(max);
            return this;
        }

        ParamsBuilder conImagenes(boolean value) {
            conImagenes = value;
            return this;
        }

        ParamsBuilder dadosDeBaja() {
            dadosDeBaja = true;
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
                    null, superficieMin, superficieMax, conImagenes, dadosDeBaja, page, size, sort);
        }
    }
}
