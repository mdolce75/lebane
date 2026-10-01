package com.lebane.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lebane.support.PostgresContainer;

/**
 * Integración real con PostgreSQL (Testcontainers, requiere Docker): con la base disponible
 * health, liveness y readiness responden 200 UP, sin exponer detalles.
 */
@ImportTestcontainers(PostgresContainer.class)
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lebane.security.actuator.username=probe",
        "lebane.security.actuator.password=probe-secret"
})
class ActuatorEndpointsIT {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void healthIsUpWithoutDetails() throws Exception {
        // La raíz lista los nombres de los grupos (liveness/readiness): no es información sensible.
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.has("components")).isFalse();
        assertThat(body.has("details")).isFalse();
    }

    @Test
    void livenessIsUp() throws Exception {
        assertUp("/actuator/health/liveness");
    }

    @Test
    void readinessIsUpWhenDatabaseIsAvailable() throws Exception {
        assertUp("/actuator/health/readiness");
    }

    @Test
    void requestIdIsEchoed() {
        ResponseEntity<String> response = rest.exchange(
                org.springframework.http.RequestEntity.get("/actuator/health/readiness")
                        .header("X-Request-Id", "it-req-42").build(),
                String.class);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("it-req-42");
    }

    @Test
    void prometheusExposesHikariAndHttpMetrics() {
        rest.getForEntity("/actuator/health", String.class);
        ResponseEntity<String> response = rest.withBasicAuth("probe", "probe-secret")
                .getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("hikaricp_connections", "http_server_requests_seconds");
    }

    private void assertUp(String path) throws Exception {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body).isEqualTo(mapper.readTree("{\"status\":\"UP\"}"));
    }
}
