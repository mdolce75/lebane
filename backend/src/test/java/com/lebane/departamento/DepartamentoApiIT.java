package com.lebane.departamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.ConsultaMapper;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.departamento.service.DepartamentoService;
import com.lebane.support.PostgresContainer;

import jakarta.persistence.EntityManagerFactory;

/**
 * Flujo completo por HTTP contra PostgreSQL real (alta, detalle, edición con If-Match, consultas y errores) y
 * verificación de que el detalle ejecuta una cantidad fija de sentencias SQL (sin N+1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@ImportTestcontainers(PostgresContainer.class)
class DepartamentoApiIT {

    private static final String BASE = "/api/v1/departamentos";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DepartamentoService departamentoService;
    @Autowired
    private DepartamentoRepository departamentoRepository;
    @Autowired
    private ImagenRepository imagenRepository;
    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private DepartamentoMapper departamentoMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void createReadUpdateAndConsultaLifecycle() throws Exception {
        // Alta
        ResponseEntity<String> created = rest.exchange(BASE, HttpMethod.POST, json(TestFixtures.departamentoJson()),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(created.getBody());
        long id = body.path("id").asLong();
        assertThat(created.getHeaders().getLocation()).hasToString(BASE + "/" + id);
        assertThat(created.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(body.path("codigo").asText()).matches("^DEP-[0-9A-Z]{8}$");
        assertThat(body.path("estado").asText()).isEqualTo("DISPONIBLE");
        assertThat(body.path("createdAt").asText()).isNotBlank();

        // Detalle
        ResponseEntity<String> detail = rest.getForEntity(BASE + "/" + id, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(detail.getBody()).path("direccion").path("calle").asText())
                .isEqualTo("Gorriti");

        // Edición con If-Match correcto
        String update = TestFixtures.departamentoJson().replace("\"ambientes\": 3", "\"ambientes\": 4")
                .replace("}\n}", "},\n  \"estado\": \"RESERVADO\"\n}");
        ResponseEntity<String> updated = rest.exchange(BASE + "/" + id, HttpMethod.PUT,
                json(update, "\"0\""), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getHeaders().getETag()).isEqualTo("\"1\"");
        JsonNode updatedBody = objectMapper.readTree(updated.getBody());
        assertThat(updatedBody.path("ambientes").asInt()).isEqualTo(4);
        assertThat(updatedBody.path("estado").asText()).isEqualTo("RESERVADO");

        // Edición con If-Match desactualizado: 412 y sin cambios
        ResponseEntity<String> stale = rest.exchange(BASE + "/" + id, HttpMethod.PUT, json(update, "\"0\""),
                String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(objectMapper.readTree(stale.getBody()).path("error").asText()).isEqualTo("PRECONDITION_FAILED");

        // Consulta: se registra y se refleja en el contador del detalle
        ResponseEntity<String> consulta = rest.exchange(BASE + "/" + id + "/consultas", HttpMethod.POST,
                json(TestFixtures.consultaJson()), String.class);
        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(consulta.getBody()).doesNotContain("example.com");
        JsonNode afterConsulta = objectMapper.readTree(rest.getForEntity(BASE + "/" + id, String.class).getBody());
        assertThat(afterConsulta.path("cantidadConsultas").asLong()).isEqualTo(1);
    }

    @Test
    void soldDepartamentoRejectsConsultas() throws Exception {
        String vendido = TestFixtures.departamentoJson().replace("}\n}", "},\n  \"estado\": \"VENDIDO\"\n}");
        long id = objectMapper.readTree(rest.exchange(BASE, HttpMethod.POST, json(vendido), String.class).getBody())
                .path("id").asLong();

        ResponseEntity<String> response = rest.exchange(BASE + "/" + id + "/consultas", HttpMethod.POST,
                json(TestFixtures.consultaJson()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(response.getBody()).path("error").asText())
                .isEqualTo("DEPARTAMENTO_NO_DISPONIBLE");
    }

    @Test
    void errorsFollowTheApiErrorSchema() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "it-404-req");
        ResponseEntity<String> notFound = rest.exchange(BASE + "/999999999", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = objectMapper.readTree(notFound.getBody());
        assertThat(body.path("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.path("requestId").asText()).isEqualTo("it-404-req");
        assertThat(body.path("path").asText()).isEqualTo(BASE + "/999999999");

        ResponseEntity<String> unknownRoute = rest.getForEntity("/api/v1/no-existe", String.class);
        assertThat(unknownRoute.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(objectMapper.readTree(unknownRoute.getBody()).path("error").asText()).isEqualTo("NOT_FOUND");

        ResponseEntity<String> invalid = rest.exchange(BASE, HttpMethod.POST, json("{\"titulo\": \"\"}"),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(invalid.getBody()).path("fieldErrors").has("titulo")).isTrue();
    }

    /**
     * El detalle ejecuta siempre 3 sentencias (departamento, imágenes, COUNT de consultas), tenga 0 o 5 fotos y
     * cualquier cantidad de consultas: no hay N+1.
     */
    @Test
    void detailRunsAConstantNumberOfStatements() {
        long sinFotos = crearConFotosYConsultas(0, 0);
        long conFotos = crearConFotosYConsultas(5, 7);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        departamentoService.obtenerDetalle(sinFotos);
        long statementsSinFotos = statistics.getPrepareStatementCount();

        statistics.clear();
        var detalle = departamentoService.obtenerDetalle(conFotos);
        long statementsConFotos = statistics.getPrepareStatementCount();

        assertThat(detalle.imagenes()).hasSize(5);
        assertThat(detalle.cantidadConsultas()).isEqualTo(7);
        assertThat(statementsSinFotos).isEqualTo(3);
        assertThat(statementsConFotos).isEqualTo(3);
        assertThat(statistics.getEntityFetchCount()).as("sin cargas perezosas adicionales").isZero();
    }

    private long crearConFotosYConsultas(int fotos, int consultas) {
        return transactionTemplate.execute(status -> {
            Departamento departamento = departamentoRepository.save(departamentoMapper.toNewEntity(
                    TestFixtures.departamento(3, 2, EstadoDepartamento.DISPONIBLE),
                    "IT-" + UUID.randomUUID().toString().substring(0, 12)));
            for (int i = 0; i < fotos; i++) {
                imagenRepository.save(new Imagen(departamento, "departamentos/" + UUID.randomUUID() + ".jpg",
                        "image/jpeg", 1024, i));
            }
            ConsultaMapper consultaMapper = new ConsultaMapper();
            for (int i = 0; i < consultas; i++) {
                consultaRepository.save(consultaMapper.toEntity(departamento, TestFixtures.consulta()));
            }
            return departamento.getId();
        });
    }

    private static HttpEntity<String> json(String body) {
        return json(body, null);
    }

    private static HttpEntity<String> json(String body, String ifMatch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ifMatch != null) {
            headers.setIfMatch(ifMatch);
        }
        return new HttpEntity<>(body, headers);
    }

    /**
     * Un request que rechaza el firewall de Spring Security (antes de Spring MVC) también responde con
     * {@code ApiError} en JSON, con el path original y el requestId del cliente: lo resuelve {@code ApiErrorController}.
     */
    @Test
    void requestsRejectedBeforeSpringMvcStillGetAnApiError() throws Exception {
        ResponseEntity<String> response = rest.exchange(org.springframework.http.RequestEntity
                .get(java.net.URI.create(rest.getRootUri() + BASE + ";x=1/1"))
                .header("X-Request-Id", "it-firewall-1").build(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("error").asText()).isEqualTo("BAD_REQUEST");
        assertThat(error.path("path").asText()).isEqualTo(BASE + ";x=1/1");
        assertThat(error.path("requestId").asText()).isEqualTo("it-firewall-1");
        assertThat(response.getBody()).doesNotContain("Firewall", "Exception", "semicolon");
    }
}
