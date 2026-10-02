package com.lebane.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Contrato OpenAPI de la API ({@code /v3/api-docs}, generado por springdoc desde el código):
 * <ul>
 *   <li>coincide con el spec versionado en {@code docs/openapi.json}: un cambio en la API que no actualiza el spec
 *       hace fallar el build. Para regenerarlo: {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true};</li>
 *   <li>está completo: cada operación tiene resumen, descripción y tag, y cada parámetro y cada propiedad de los
 *       modelos tienen descripción;</li>
 *   <li>los errores son coherentes: toda respuesta 4xx/5xx usa el esquema {@code ApiError}.</li>
 * </ul>
 * Corre sin base de datos (perfil {@code nodb}): el spec no depende de PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("nodb")
class OpenApiSpecTest {

    static final Path SPEC_VERSIONADO = Path.of("..", "docs", "openapi.json");

    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonNode spec;

    @BeforeEach
    void generarSpec() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        spec = mapper.readTree(json);
    }

    @Test
    void elSpecVersionadoEstaAlDia() throws Exception {
        String generado = mapper.writeValueAsString(spec) + System.lineSeparator();
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SPEC_VERSIONADO.getParent());
            Files.writeString(SPEC_VERSIONADO, generado, StandardCharsets.UTF_8);
        }
        assertThat(SPEC_VERSIONADO)
                .as("docs/openapi.json no existe: generarlo con -Dopenapi.update=true")
                .exists();
        assertThat(mapper.readTree(Files.readString(SPEC_VERSIONADO, StandardCharsets.UTF_8)))
                .as("docs/openapi.json está desactualizado respecto de la API. Regenerarlo con "
                        + "./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true y revisar el diff")
                .isEqualTo(spec);
    }

    @Test
    void documentaTodosLosEndpointsDeLaApi() {
        assertThat(spec.path("openapi").asText()).startsWith("3.");
        assertThat(nombres(spec.path("paths"))).containsExactlyInAnyOrder(
                "/api/v1/departamentos",
                "/api/v1/departamentos/{id}",
                "/api/v1/departamentos/{id}/consultas",
                "/api/v1/departamentos/{departamentoId}/imagenes",
                "/api/v1/departamentos/{departamentoId}/imagenes/{imagenId}",
                "/api/v1/direcciones/autocompletar");
        // Actuator no es parte del contrato público.
        assertThat(nombres(spec.path("paths"))).noneMatch(path -> path.startsWith("/actuator"));
    }

    @Test
    void cadaOperacionTieneResumenDescripcionTagYRespuestas() {
        List<String> faltantes = new ArrayList<>();
        operaciones().forEach((nombre, operacion) -> {
            for (String campo : List.of("summary", "description", "operationId")) {
                if (operacion.path(campo).asText().isBlank()) {
                    faltantes.add(nombre + " sin " + campo);
                }
            }
            if (operacion.path("tags").isEmpty()) {
                faltantes.add(nombre + " sin tag");
            }
            List<String> codigos = nombres(operacion.path("responses"));
            if (codigos.stream().noneMatch(codigo -> codigo.startsWith("2"))) {
                faltantes.add(nombre + " sin respuesta 2xx");
            }
            for (String obligatorio : List.of("400", "500")) {
                if (!codigos.contains(obligatorio)) {
                    faltantes.add(nombre + " sin respuesta " + obligatorio);
                }
            }
            operacion.path("parameters").forEach(parametro -> {
                if (parametro.path("description").asText().isBlank()) {
                    faltantes.add(nombre + ": parámetro " + parametro.path("name").asText() + " sin descripción");
                }
            });
        });
        assertThat(faltantes).isEmpty();
    }

    @Test
    void todasLasRespuestasDeErrorUsanApiError() {
        List<String> incorrectas = new ArrayList<>();
        operaciones().forEach((nombre, operacion) -> operacion.path("responses").properties().forEach(respuesta -> {
            if (respuesta.getKey().startsWith("2")) {
                return;
            }
            JsonNode resuelta = resolver(respuesta.getValue());
            String esquema = resuelta.path("content").path("application/json").path("schema").path("$ref").asText();
            if (!esquema.equals("#/components/schemas/ApiError")) {
                incorrectas.add(nombre + " " + respuesta.getKey() + " -> " + (esquema.isBlank() ? "sin esquema" : esquema));
            }
            if (resuelta.path("content").path("application/json").path("examples").isEmpty()) {
                incorrectas.add(nombre + " " + respuesta.getKey() + " sin ejemplos");
            }
        }));
        assertThat(incorrectas).isEmpty();
    }

    @Test
    void cadaPropiedadDeLosModelosTieneDescripcion() {
        List<String> sinDescripcion = new ArrayList<>();
        spec.path("components").path("schemas").properties().forEach(modelo -> {
            if (modelo.getValue().path("description").asText().isBlank()) {
                sinDescripcion.add(modelo.getKey());
            }
            modelo.getValue().path("properties").properties().forEach(propiedad -> {
                JsonNode p = propiedad.getValue();
                // Una propiedad que referencia otro modelo hereda la descripción de ese modelo.
                boolean documentada = !p.path("description").asText().isBlank() || p.has("$ref")
                        || p.path("allOf").size() > 0;
                if (!documentada) {
                    sinDescripcion.add(modelo.getKey() + "." + propiedad.getKey());
                }
            });
        });
        assertThat(sinDescripcion).isEmpty();
    }

    @Test
    void elIdDeCorrelacionYLaConcurrenciaOptimistaEstanDocumentados() {
        JsonNode editar = spec.path("paths").path("/api/v1/departamentos/{id}").path("put");
        assertThat(nombresDeParametros(editar)).contains("If-Match", "X-Request-Id");
        assertThat(editar.path("responses").path("200").path("headers").has("ETag")).isTrue();
        assertThat(editar.path("responses").has("412")).isTrue();

        JsonNode crear = spec.path("paths").path("/api/v1/departamentos").path("post");
        assertThat(crear.path("responses").path("201").path("headers").has("Location")).isTrue();

        operaciones().forEach((nombre, operacion) ->
                assertThat(nombresDeParametros(operacion)).as(nombre).contains("X-Request-Id"));
    }

    private Map<String, JsonNode> operaciones() {
        Map<String, JsonNode> operaciones = new java.util.LinkedHashMap<>();
        spec.path("paths").properties().forEach(path -> path.getValue().properties().forEach(metodo -> {
            if (METODOS.contains(metodo.getKey())) {
                operaciones.put(metodo.getKey().toUpperCase() + " " + path.getKey(), metodo.getValue());
            }
        }));
        assertThat(operaciones).isNotEmpty();
        return operaciones;
    }

    private JsonNode resolver(JsonNode nodo) {
        String ref = nodo.path("$ref").asText();
        if (ref.isBlank()) {
            return nodo;
        }
        assertThat(ref).startsWith("#/components/responses/");
        JsonNode resuelto = spec.path("components").path("responses").path(ref.substring(ref.lastIndexOf('/') + 1));
        assertThat(resuelto.isMissingNode()).as("referencia inexistente: %s", ref).isFalse();
        return resuelto;
    }

    private static List<String> nombresDeParametros(JsonNode operacion) {
        List<String> nombres = new ArrayList<>();
        operacion.path("parameters").forEach(parametro -> nombres.add(parametro.path("name").asText()));
        return nombres;
    }

    private static List<String> nombres(JsonNode objeto) {
        List<String> nombres = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }
}
