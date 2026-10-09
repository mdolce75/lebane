package com.lebane.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.seed.DevDataSeeder;
import com.lebane.support.MinioContainers;
import com.lebane.support.PostgresContainer;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;

/**
 * Fotos de punta a punta contra MinIO y PostgreSQL reales: subida por HTTP, lectura pública desde MinIO, detalle y
 * listado, límite de 5, validación por contenido, eliminación y seed con fotos. También verifica que los logs de
 * storage no contengan las credenciales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lebane.storage.create-bucket=true", "lebane.seed.enabled=true"})
@ImportTestcontainers(PostgresContainer.class)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class StorageIT {

    private static final String BUCKET = "it-images";

    @Container
    static final MinIOContainer MINIO = MinioContainers.create();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("lebane.storage.endpoint", MINIO::getS3URL);
        registry.add("lebane.storage.public-url", MINIO::getS3URL);
        registry.add("lebane.storage.access-key", () -> MinioContainers.ACCESS_KEY);
        registry.add("lebane.storage.secret-key", () -> MinioContainers.SECRET_KEY);
        registry.add("lebane.storage.bucket", () -> BUCKET);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private MinioClient minio;
    @Autowired
    private DevDataSeeder seeder;
    @Autowired
    private DepartamentoRepository departamentoRepository;
    @Autowired
    private ImagenRepository imagenRepository;

    @Test
    void uploadedImageIsStoredAndPubliclyReadable(CapturedOutput output) throws Exception {
        long id = crearDepartamento();
        byte[] png = TestImages.realPng();

        ResponseEntity<String> response = subir(id, png, "foto.jpg"); // nombre engañoso: manda el contenido

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode imagen = objectMapper.readTree(response.getBody());
        assertThat(imagen.path("contentType").asText()).isEqualTo("image/png");
        assertThat(imagen.path("posicion").asInt()).isZero();
        String url = imagen.path("url").asText();
        assertThat(url).startsWith(MINIO.getS3URL() + "/" + BUCKET + "/departamentos/" + id + "/").endsWith(".png");

        // Lectura anónima desde el navegador (bucket con lectura pública).
        HttpResponse<byte[]> publica = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(publica.statusCode()).isEqualTo(200);
        assertThat(publica.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(publica.body()).isEqualTo(png);

        JsonNode detalle = objectMapper.readTree(rest.getForObject("/api/departamentos/" + id, String.class));
        assertThat(detalle.path("imagenes")).hasSize(1);
        assertThat(detalle.path("imagenes").get(0).path("url").asText()).isEqualTo(url);

        // Logs de storage estructurados y sin credenciales.
        assertThat(output.getOut()).contains("Storage upload completed").contains("\"objectKey\"");
        assertThat(output.getAll()).doesNotContain(MinioContainers.SECRET_KEY);
    }

    @Test
    void enforcesTheFiveImageLimitInDatabaseAndStorage() throws Exception {
        long id = crearDepartamento();
        for (int i = 0; i < 5; i++) {
            assertThat(subir(id, TestImages.realPng(), "f" + i + ".png").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<String> sexta = subir(id, TestImages.realPng(), "f5.png");

        assertThat(sexta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(sexta.getBody()).path("error").asText())
                .isEqualTo("LIMITE_IMAGENES_ALCANZADO");
        assertThat(objetos("departamentos/" + id + "/")).isEqualTo(5);
        assertThat(imagenRepository.countByDepartamentoId(id)).isEqualTo(5);
        JsonNode listado = objectMapper.readTree(rest.getForObject(
                "/api/departamentos?q=" + "IT Storage " + id + "&cantidad=1", String.class));
        assertThat(listado.path("content").get(0).path("cantidadImagenes").asInt()).isEqualTo(5);
        assertThat(listado.path("content").get(0).path("imagenPrincipalUrl").asText()).contains("/departamentos/" + id);
    }

    @Test
    void rejectsContentThatIsNotAnImage() throws Exception {
        long id = crearDepartamento();

        ResponseEntity<String> response = subir(id, "<html>no</html>".getBytes(StandardCharsets.UTF_8), "foto.jpg");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("fieldErrors").path("archivo").asText())
                .isEqualTo("debe ser una imagen JPEG, PNG o WebP");
        assertThat(objetos("departamentos/" + id + "/")).isZero();
    }

    @Test
    void deleteRemovesTheRowAndTheObject() throws Exception {
        long id = crearDepartamento();
        JsonNode imagen = objectMapper.readTree(subir(id, TestImages.realPng(), "a.png").getBody());

        ResponseEntity<Void> response = rest.exchange("/api/departamentos/" + id + "/imagenes/"
                + imagen.path("id").asLong(), HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(objetos("departamentos/" + id + "/")).isZero();
        assertThat(http.send(HttpRequest.newBuilder(URI.create(imagen.path("url").asText())).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(rest.getForObject("/api/departamentos/" + id, String.class))
                .path("imagenes")).isEmpty();
    }

    /** El seed sube fotos con el mismo servicio que la API y es idempotente. */
    @Test
    void seedAddsPhotosOnce() {
        long seed1 = departamentoRepository.findIdByCodigo("SEED-0001").orElseThrow();
        long seed3 = departamentoRepository.findIdByCodigo("SEED-0003").orElseThrow();
        long seed4 = departamentoRepository.findIdByCodigo("SEED-0004").orElseThrow();
        assertThat(imagenRepository.countByDepartamentoId(seed1)).isEqualTo(1);
        assertThat(imagenRepository.countByDepartamentoId(seed3)).isEqualTo(3);
        assertThat(imagenRepository.countByDepartamentoId(seed4)).isZero();

        seeder.run(new DefaultApplicationArguments());

        assertThat(imagenRepository.countByDepartamentoId(seed3)).isEqualTo(3);
        assertThat(objetos("departamentos/" + seed3 + "/")).isEqualTo(3);
    }

    /** Alta del enunciado: datos y fotos en el mismo request, 202 con el recurso creado y sus fotos. */
    @Test
    void altaConFotosEnUnSoloRequest() throws Exception {
        ResponseEntity<String> response = altaMultipart(TestFixtures.departamentoJson(TestFixtures.unidadUnica()),
                TestImages.realPng(), TestImages.realPng());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode creado = objectMapper.readTree(response.getBody());
        assertThat(creado.path("imagenes")).hasSize(2);
        assertThat(creado.path("imagenes").get(0).path("posicion").asInt()).isZero();
        String url = creado.path("imagenes").get(1).path("url").asText();
        HttpResponse<byte[]> publica = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(publica.statusCode()).isEqualTo(200);

        JsonNode detalle = objectMapper.readTree(rest.getForObject(
                "/api/departamentos/" + creado.path("id").asLong(), String.class));
        assertThat(detalle.path("imagenes")).hasSize(2);
    }

    /** Todo o nada: con una foto inválida no se crea el departamento ni se sube ninguna foto. */
    @Test
    void altaConUnaFotoInvalidaNoCreaNada() throws Exception {
        long departamentosAntes = departamentoRepository.count();
        int objetosAntes = objetos("departamentos/altas/");

        ResponseEntity<String> response = altaMultipart(TestFixtures.departamentoJson(TestFixtures.unidadUnica()),
                TestImages.realPng(), "<html>no soy una foto</html>".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("fieldErrors").has("imagenes[1]")).isTrue();
        assertThat(departamentoRepository.count()).isEqualTo(departamentosAntes);
        assertThat(objetos("departamentos/altas/")).isEqualTo(objetosAntes);
    }

    /** Un alta rechazada por las reglas de negocio (dirección ocupada) no deja fotos en el storage. */
    @Test
    void altaRechazadaNoDejaFotosSueltas() throws Exception {
        String alta = TestFixtures.departamentoJson(TestFixtures.unidadUnica());
        assertThat(altaMultipart(alta).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int objetosAntes = objetos("departamentos/altas/");

        ResponseEntity<String> duplicado = altaMultipart(alta, TestImages.realPng());

        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objetos("departamentos/altas/")).isEqualTo(objetosAntes);
    }

    private ResponseEntity<String> altaMultipart(String departamento, byte[]... fotos) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        form.add("departamento", new HttpEntity<>(departamento, json));
        for (int i = 0; i < fotos.length; i++) {
            String nombre = "foto" + i + ".png";
            form.add("imagenes", new ByteArrayResource(fotos[i]) {
                @Override
                public String getFilename() {
                    return nombre;
                }
            });
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/departamentos", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private long crearDepartamento() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String marca = "IT Storage " + UUID.randomUUID().toString().substring(0, 8);
        String body = TestFixtures.departamentoJson().replace("3 ambientes en Palermo", marca);
        JsonNode creado = objectMapper.readTree(rest.exchange("/api/departamentos", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class).getBody());
        long id = creado.path("id").asLong();
        // Título único y buscable: "IT Storage <id>"
        String update = TestFixtures.departamentoJson().replace("3 ambientes en Palermo", "IT Storage " + id);
        rest.exchange("/api/departamentos/" + id, HttpMethod.PUT, new HttpEntity<>(update, headers),
                String.class);
        return id;
    }

    private ResponseEntity<String> subir(long id, byte[] contenido, String nombre) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombre;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/departamentos/" + id + "/imagenes", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    private int objetos(String prefijo) {
        int count = 0;
        for (var ignored : minio.listObjects(ListObjectsArgs.builder().bucket(BUCKET).prefix(prefijo)
                .recursive(true).build())) {
            count++;
        }
        return count;
    }
}
