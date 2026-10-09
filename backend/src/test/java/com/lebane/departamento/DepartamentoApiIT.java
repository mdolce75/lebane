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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;

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

    private static final String BASE = "/api/departamentos";

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
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
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

    /** Las consultas de cada departamento se pueden leer, paginadas y de la más reciente a la más antigua. */
    @Test
    void consultasSeListanPaginadas() throws Exception {
        long id = crearDisponible();
        String consultas = BASE + "/" + id + "/consultas";
        for (String usuario : new String[] {"primera", "segunda", "tercera"}) {
            assertThat(rest.exchange(consultas, HttpMethod.POST,
                    json(TestFixtures.consultaJson().replace("ana.perez@", usuario + "@")), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<String> pagina = rest.getForEntity(consultas + "?size=2", String.class);
        assertThat(pagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(pagina.getBody());
        assertThat(body.path("page").path("totalElements").asLong()).isEqualTo(3);
        assertThat(body.path("page").path("totalPages").asLong()).isEqualTo(2);
        assertThat(body.path("content")).hasSize(2);
        assertThat(body.path("content").get(0).path("email").asText()).isEqualTo("tercera@example.com");
        assertThat(body.path("content").get(0).path("mensaje").asText()).isNotBlank();
        assertThat(body.path("content").get(1).path("email").asText()).isEqualTo("segunda@example.com");

        JsonNode segunda = objectMapper.readTree(rest.getForEntity(consultas + "?size=2&page=1", String.class).getBody());
        assertThat(segunda.path("content")).hasSize(1);
        assertThat(segunda.path("content").get(0).path("email").asText()).isEqualTo("primera@example.com");

        assertError(rest.getForEntity(BASE + "/999999/consultas", String.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(rest.getForEntity(consultas + "?size=0", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** El autocompletado (p. ej. Georef) trae coordenadas con más de 6 decimales: se aceptan y se redondean. */
    @Test
    void coordenadasConMasDecimalesSeRedondean() throws Exception {
        String conMuchosDecimales = TestFixtures.departamentoJson(TestFixtures.unidadUnica())
                .replace("-34.5889", "-34.59581734221").replace("-58.4305", "-58.39391185746");
        ResponseEntity<String> created = rest.exchange(BASE, HttpMethod.POST, json(conMuchosDecimales), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode direccion = objectMapper.readTree(created.getBody()).path("direccion");
        assertThat(direccion.path("latitud").decimalValue()).isEqualByComparingTo("-34.595817");
        assertThat(direccion.path("longitud").decimalValue()).isEqualByComparingTo("-58.393912");
    }

    @Test
    void soldDepartamentoRejectsConsultas() throws Exception {
        long id = crearDisponible();
        assertThat(cambiarEstado(id, "VENDIDO").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = rest.exchange(BASE + "/" + id + "/consultas", HttpMethod.POST,
                json(TestFixtures.consultaJson()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(response.getBody()).path("error").asText())
                .isEqualTo("DEPARTAMENTO_NO_DISPONIBLE");
    }

    /** Ciclo de vida por HTTP: DISPONIBLE ⇄ RESERVADO → VENDIDO, y VENDIDO es un registro cerrado. */
    @Test
    void lifecycleRulesAreEnforcedOverHttp() throws Exception {
        // No se publica directamente como vendido.
        ResponseEntity<String> altaVendido = rest.exchange(BASE, HttpMethod.POST, json(conEstado("VENDIDO")),
                String.class);
        assertThat(altaVendido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(altaVendido.getBody()).path("error").asText())
                .isEqualTo("TRANSICION_DE_ESTADO_INVALIDA");

        long id = crearDisponible();
        assertThat(cambiarEstado(id, "RESERVADO").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cambiarEstado(id, "DISPONIBLE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cambiarEstado(id, "VENDIDO").getStatusCode()).isEqualTo(HttpStatus.OK);

        // Vendido: no vuelve a otro estado, no se edita (ni siquiera sin cambiar el estado) y sus fotos no cambian.
        for (String estado : new String[] {"DISPONIBLE", "VENDIDO"}) {
            ResponseEntity<String> edicion = cambiarEstado(id, estado);
            assertThat(edicion.getStatusCode()).as(estado).isEqualTo(HttpStatus.CONFLICT);
            assertThat(objectMapper.readTree(edicion.getBody()).path("error").asText())
                    .isEqualTo("DEPARTAMENTO_NO_DISPONIBLE");
        }
        HttpHeaders multipart = new HttpHeaders();
        multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(new byte[] {(byte) 0x89, 'P', 'N', 'G'}) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        });
        ResponseEntity<String> foto = rest.exchange(BASE + "/" + id + "/imagenes", HttpMethod.POST,
                new HttpEntity<>(form, multipart), String.class);
        assertThat(foto.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(foto.getBody()).path("error").asText()).isEqualTo("DEPARTAMENTO_NO_DISPONIBLE");

        JsonNode detalle = objectMapper.readTree(rest.getForEntity(BASE + "/" + id, String.class).getBody());
        assertThat(detalle.path("estado").asText()).isEqualTo("VENDIDO");
    }

    /** Reglas de duplicados por HTTP: la misma dirección publicada dos veces y la misma consulta repetida. */
    @Test
    void duplicateRulesAreEnforcedOverHttp() throws Exception {
        String alta = TestFixtures.departamentoJson(TestFixtures.unidadUnica());
        ResponseEntity<String> primero = rest.exchange(BASE, HttpMethod.POST, json(alta), String.class);
        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long id = objectMapper.readTree(primero.getBody()).path("id").asLong();

        // La misma dirección (con otras mayúsculas) no se publica dos veces, ni por alta ni por edición.
        assertError(rest.exchange(BASE, HttpMethod.POST, json(alta.replace("\"Gorriti\"", "\"GORRITI\"")),
                String.class), HttpStatus.CONFLICT, "AVISO_DUPLICADO");
        long otro = crearDisponible();
        assertError(rest.exchange(BASE + "/" + otro, HttpMethod.PUT, json(alta), String.class),
                HttpStatus.CONFLICT, "AVISO_DUPLICADO");

        // Editar sin cambiar la dirección no choca consigo mismo; una vez vendido, la dirección se puede publicar.
        ResponseEntity<String> vendido = rest.exchange(BASE + "/" + id, HttpMethod.PUT,
                json(alta.replace("}\n}", "},\n  \"estado\": \"VENDIDO\"\n}")), String.class);
        assertThat(vendido.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange(BASE, HttpMethod.POST, json(alta), String.class).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        // El mismo email (sin distinguir mayúsculas) no consulta dos veces por el mismo departamento en 24 horas.
        String consultas = BASE + "/" + otro + "/consultas";
        assertThat(rest.exchange(consultas, HttpMethod.POST, json(TestFixtures.consultaJson()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertError(rest.exchange(consultas, HttpMethod.POST,
                json(TestFixtures.consultaJson().replace("ana.perez@example.com", "Ana.Perez@Example.com")),
                String.class), HttpStatus.CONFLICT, "CONSULTA_DUPLICADA");
        assertThat(rest.exchange(consultas, HttpMethod.POST,
                json(TestFixtures.consultaJson().replace("ana.perez@", "otra.persona@")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode detalle = objectMapper.readTree(rest.getForEntity(BASE + "/" + otro, String.class).getBody());
        assertThat(detalle.path("cantidadConsultas").asLong()).isEqualTo(2);
    }

    /**
     * Baja lógica y reactivación por HTTP: sale del listado, no admite cambios, conserva los datos, libera la dirección
     * y se reactiva solo si la dirección sigue libre.
     */
    @Test
    void bajaYReactivacionOverHttp() throws Exception {
        String marca = "IT baja " + UUID.randomUUID().toString().substring(0, 8);
        String alta = TestFixtures.departamentoJson(TestFixtures.unidadUnica())
                .replace("3 ambientes en Palermo", marca);
        ResponseEntity<String> creado = rest.exchange(BASE, HttpMethod.POST, json(alta), String.class);
        long id = objectMapper.readTree(creado.getBody()).path("id").asLong();
        String url = BASE + "/" + id;
        assertThat(rest.exchange(url + "/consultas", HttpMethod.POST, json(TestFixtures.consultaJson()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Con un ETag desactualizado no se da de baja.
        assertError(rest.exchange(url, HttpMethod.DELETE, json("", "\"99\""), String.class),
                HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED");
        ResponseEntity<String> baja = rest.exchange(url, HttpMethod.DELETE,
                json("", creado.getHeaders().getETag()), String.class);
        assertThat(baja.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Se lee (con fechaBaja) pero no admite cambios; sale del listado y aparece con dadosDeBaja=true.
        ResponseEntity<String> detalle = rest.getForEntity(url, String.class);
        assertThat(detalle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(detalle.getBody()).path("fechaBaja").isNull()).isFalse();
        assertError(rest.exchange(url, HttpMethod.PUT, json(alta), String.class),
                HttpStatus.CONFLICT, "DEPARTAMENTO_DADO_DE_BAJA");
        assertError(rest.exchange(url + "/consultas", HttpMethod.POST,
                json(TestFixtures.consultaJson().replace("ana.perez@", "otra@")), String.class),
                HttpStatus.CONFLICT, "DEPARTAMENTO_DADO_DE_BAJA");
        assertError(rest.exchange(url, HttpMethod.DELETE, json("", null), String.class),
                HttpStatus.CONFLICT, "DEPARTAMENTO_DADO_DE_BAJA");
        assertThat(total(marca, false)).isZero();
        assertThat(total(marca, true)).isEqualTo(1);
        assertThat(consultaRepository.countByDepartamentoId(id)).isEqualTo(1);

        // La dirección queda libre: otro aviso la ocupa y la reactivación choca con él.
        ResponseEntity<String> otro = rest.exchange(BASE, HttpMethod.POST, json(alta), String.class);
        assertThat(otro.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertError(rest.exchange(url + "/reactivacion", HttpMethod.POST, json("", null), String.class),
                HttpStatus.CONFLICT, "AVISO_DUPLICADO");

        // Dado de baja el otro, se reactiva con la versión leída y vuelve al listado.
        long otroId = objectMapper.readTree(otro.getBody()).path("id").asLong();
        assertThat(rest.exchange(BASE + "/" + otroId, HttpMethod.DELETE, json("", null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String etag = rest.getForEntity(url, String.class).getHeaders().getETag();
        ResponseEntity<String> reactivado = rest.exchange(url + "/reactivacion", HttpMethod.POST, json("", etag),
                String.class);
        assertThat(reactivado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(reactivado.getBody()).path("fechaBaja").isNull()).isTrue();
        assertThat(reactivado.getHeaders().getETag()).isNotEqualTo(etag);
        assertError(rest.exchange(url + "/reactivacion", HttpMethod.POST, json("", null), String.class),
                HttpStatus.CONFLICT, "DEPARTAMENTO_NO_DADO_DE_BAJA");
        assertThat(total(marca, false)).isEqualTo(1);
    }

    private long total(String marca, boolean dadosDeBaja) throws Exception {
        String url = BASE + "?q=" + marca.substring(3) + (dadosDeBaja ? "&dadosDeBaja=true" : "");
        return objectMapper.readTree(rest.getForEntity(url, String.class).getBody())
                .path("page").path("totalElements").asLong();
    }

    private void assertError(ResponseEntity<String> response, HttpStatus status, String error) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(objectMapper.readTree(response.getBody()).path("error").asText()).isEqualTo(error);
    }

    private long crearDisponible() throws Exception {
        ResponseEntity<String> created = rest.exchange(BASE, HttpMethod.POST, json(TestFixtures.departamentoJson()),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return objectMapper.readTree(created.getBody()).path("id").asLong();
    }

    private ResponseEntity<String> cambiarEstado(long id, String estado) {
        return rest.exchange(BASE + "/" + id, HttpMethod.PUT, json(conEstado(estado)), String.class);
    }

    private static String conEstado(String estado) {
        return TestFixtures.departamentoJson().replace("}\n}", "},\n  \"estado\": \"" + estado + "\"\n}");
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

        ResponseEntity<String> unknownRoute = rest.getForEntity("/api/no-existe", String.class);
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
