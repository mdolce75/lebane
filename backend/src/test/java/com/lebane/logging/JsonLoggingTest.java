package com.lebane.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifica el formato JSON de los logs, los campos estructurados del access log, la inclusión del
 * requestId y el enmascarado de secretos.
 *
 * <p>Logback se configura una sola vez por JVM mientras los contextos de test cacheados siguen abiertos, por lo
 * que este test no sobrescribe propiedades de logging (dependería del orden de ejecución): usa los defaults
 * (LOG_FORMAT=json, Logstash deshabilitado) y valida la presencia de los campos comunes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("nodb")
@ExtendWith(OutputCaptureExtension.class)
class JsonLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(JsonLoggingTest.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accessLogIsStructuredJsonWithCorrelationId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/info").header("X-Request-Id", "json-req-0001"));

        JsonNode event = findEvent(output, "json-req-0001", "com.lebane.access")
                .orElseThrow(() -> new AssertionError("No se encontró el access log JSON:\n" + output));

        assertThat(event.hasNonNull("@timestamp")).isTrue();
        assertThat(event.path("level").asText()).isEqualTo("INFO");
        assertThat(event.path("logger").asText()).isEqualTo("com.lebane.access");
        assertThat(event.path("thread").asText()).isNotBlank();
        assertThat(event.path("message").asText()).contains("GET /actuator/info");
        assertThat(event.path("service").asText()).isNotBlank();
        assertThat(event.path("environment").asText()).isNotBlank();
        assertThat(event.path("application").asText()).isNotBlank();
        assertThat(event.path("requestId").asText()).isEqualTo("json-req-0001");
        assertThat(event.path("method").asText()).isEqualTo("GET");
        assertThat(event.path("path").asText()).isEqualTo("/actuator/info");
        assertThat(event.path("status").asInt()).isEqualTo(200);
        assertThat(event.path("durationMs").isNumber()).isTrue();
        assertThat(event.has("remoteAddress")).isTrue();
    }

    @Test
    void secretsAreMaskedInLogs(CapturedOutput output) {
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, "mask-req-0001");
        try {
            log.info("Conectando con password=SuperSecret123 y header Bearer abc.def.ghi {} {}",
                    kv("secretKey", "AnotherSecret456"),
                    kv("url", "http://minio/obj?X-Amz-Credential=AKIAXXX&X-Amz-Signature=deadbeef"));
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        }

        String all = output.getAll();
        assertThat(all).contains("mask-req-0001");
        assertThat(all).doesNotContain("SuperSecret123", "AnotherSecret456", "abc.def.ghi", "deadbeef", "AKIAXXX");
    }

    @Test
    void everyLogLineIsValidJson(CapturedOutput output) throws Exception {
        log.info("linea de prueba {}", kv("errorCode", "TEST"));
        mockMvc.perform(get("/actuator/info"));

        List<String> lines = output.getOut().lines().filter(line -> !line.isBlank()).toList();
        assertThat(lines).isNotEmpty();
        for (String line : lines) {
            assertThat(mapper.readTree(line).isObject()).as("Línea no JSON: %s", line).isTrue();
        }
    }

    @Test
    void logbackConfigurationHasNoWarnings() {
        // Los warnings de configuración de Logback se imprimen en texto plano y romperían el stream JSON.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertThat(new StatusUtil(context).getHighestLevel(0))
                .as("Estados de Logback: %s", context.getStatusManager().getCopyOfStatusList())
                .isLessThan(Status.WARN);
    }

    private Optional<JsonNode> findEvent(CapturedOutput output, String requestId, String logger) {
        return output.getOut().lines()
                .filter(line -> line.startsWith("{") && line.contains(requestId))
                .map(this::parse)
                .flatMap(Optional::stream)
                .filter(node -> logger.equals(node.path("logger").asText()))
                .findFirst();
    }

    private Optional<JsonNode> parse(String line) {
        try {
            return Optional.of(mapper.readTree(line));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
