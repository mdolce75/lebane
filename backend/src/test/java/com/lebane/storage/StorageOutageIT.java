package com.lebane.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.lebane.resilience.ResilientExecutor;
import com.lebane.storage.service.ObjectStorageService;
import com.lebane.support.MinioContainers;
import com.lebane.support.PostgresContainer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * MinIO cae con la aplicación en marcha: las subidas responden 503 sin detalles internos, tras los reintentos el
 * circuito se abre y las siguientes se rechazan sin tocar la red; el resto de la API y el readiness siguen OK
 * (MinIO no es crítico para atender tráfico). Contenedor propio porque se detiene.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lebane.storage.create-bucket=true", "R4J_RETRY_WAIT=50ms"})
@ImportTestcontainers(PostgresContainer.class)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class StorageOutageIT {

    @Container
    static final MinIOContainer MINIO = MinioContainers.create();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("lebane.storage.endpoint", MINIO::getS3URL);
        registry.add("lebane.storage.public-url", MINIO::getS3URL);
        registry.add("lebane.storage.access-key", () -> MinioContainers.ACCESS_KEY);
        registry.add("lebane.storage.secret-key", () -> MinioContainers.SECRET_KEY);
        registry.add("lebane.storage.bucket", () -> "outage-images");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ResilientExecutor resilience;

    @Test
    void storageOutageDegradesOnlyUploads(CapturedOutput output) throws Exception {
        long id = crearDepartamento();
        assertThat(subir(id).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        MINIO.stop();

        // Dos subidas: 3 intentos cada una -> 6 fallos -> el circuito (mínimo 5 llamadas, 50 %) se abre.
        for (int i = 0; i < 2; i++) {
            ResponseEntity<String> response = subir(id);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            JsonNode error = objectMapper.readTree(response.getBody());
            assertThat(error.path("error").asText()).isEqualTo("STORAGE_UNAVAILABLE");
            assertThat(error.path("requestId").asText()).isNotBlank();
            assertThat(response.getBody()).doesNotContain(MINIO.getHost() + ":", "Exception", "minio:");
        }
        assertThat(resilience.state(ObjectStorageService.INSTANCE)).isEqualTo(CircuitBreaker.State.OPEN);

        long start = System.nanoTime();
        ResponseEntity<String> rechazada = subir(id);
        long rechazadaMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(rechazada.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(rechazadaMs).as("rechazo inmediato: sin intentos de red ni esperas de reintento")
                .isLessThan(1_000);

        // El resto de la aplicación sigue atendiendo.
        assertThat(rest.getForEntity("/api/v1/departamentos/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/v1/departamentos?size=1", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<String> readiness = rest.getForEntity("/actuator/health/readiness", String.class);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);

        String logs = output.getOut();
        assertThat(logs).contains("Retrying call", "Retries exhausted", "Storage operation failed",
                "Circuit breaker opened: dependency degraded", "Call rejected: circuit breaker is open",
                "\"circuitBreaker\":\"storage\"", "\"provider\":\"minio\"");
        assertThat(output.getAll()).doesNotContain(MinioContainers.SECRET_KEY);
    }

    private long crearDepartamento() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = TestFixtures.departamentoJson().replace("3 ambientes en Palermo",
                "Outage " + UUID.randomUUID().toString().substring(0, 8));
        return objectMapper.readTree(rest.exchange("/api/v1/departamentos", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class).getBody()).path("id").asLong();
    }

    private ResponseEntity<String> subir(long id) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(TestImages.realPng()) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/v1/departamentos/" + id + "/imagenes", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }
}
