package com.lebane.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lebane.support.PostgresContainer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Con PostgreSQL disponible y <b>todas</b> las dependencias no críticas caídas a la vez (MinIO, proveedor externo de
 * direcciones y Logstash habilitado pero inalcanzable), la instancia sigue viva y lista: liveness y readiness
 * responden 200 {@code {"status":"UP"}} enseguida. Que esas dependencias estén realmente caídas se comprueba en el
 * mismo test (autocompletado degradado, appender de Logstash activo).
 */
@ImportTestcontainers(PostgresContainer.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // MinIO: classpath:/config/application.properties ya lo apunta a 127.0.0.1:1 (conexión rechazada).
        "lebane.address.provider=external",
        "lebane.address.url=http://127.0.0.1:1",
        "lebane.logging.logstash.enabled=true",
        "lebane.logging.logstash.host=127.0.0.1",
        "lebane.logging.logstash.port=1"
})
class NonCriticalDependenciesDownIT {

    /** Holgado para una máquina cargada, pero muy por debajo de los timeouts de MinIO, Georef y TCP. */
    private static final Duration PROBE_BUDGET = Duration.ofSeconds(1);

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void probesStayUpAndFastWhileNonCriticalDependenciesAreDown() throws Exception {
        // Las dependencias no críticas están efectivamente caídas en este contexto.
        assertThat(((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(Logger.ROOT_LOGGER_NAME).getAppender("LOGSTASH")).isNotNull();
        JsonNode autocompletado = mapper.readTree(
                rest.getForObject("/api/v1/direcciones/autocompletar?q=Gorriti 4850", String.class));
        assertThat(autocompletado.path("degradado").asBoolean()).isTrue();

        for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness", "/actuator/health"}) {
            long start = System.nanoTime();
            ResponseEntity<String> response = rest.getForEntity(probe, String.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(response.getStatusCode()).as(probe).isEqualTo(HttpStatus.OK);
            assertThat(mapper.readTree(response.getBody()).path("status").asText()).as(probe).isEqualTo("UP");
            assertThat(elapsed).as(probe + " no debe esperar a dependencias externas").isLessThan(PROBE_BUDGET);
        }
        assertThat(mapper.readTree(rest.getForObject("/actuator/health/readiness", String.class)))
                .isEqualTo(mapper.readTree("{\"status\":\"UP\"}"));
    }
}
