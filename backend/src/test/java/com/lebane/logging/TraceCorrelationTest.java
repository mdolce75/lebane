package com.lebane.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.LoggerContext;
import net.logstash.logback.appender.LogstashTcpSocketAppender;

/**
 * Correlación en los logs con tracing activo (como en producción): cada request tiene {@code requestId},
 * {@code traceId} y {@code spanId}; un {@code traceparent} W3C entrante se respeta (la traza continúa la del
 * llamador). También cubre los campos de error y que Logstash deshabilitado no agrega su appender.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("nodb")
@ExtendWith(OutputCaptureExtension.class)
class TraceCorrelationTest {

    private static final Logger log = LoggerFactory.getLogger(TraceCorrelationTest.class);
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_SPAN = "00f067aa0ba902b7";

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accessLogCarriesRequestTraceAndSpanIds(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/info").header("X-Request-Id", "trace-req-1"))
                .andExpect(header().string("X-Request-Id", "trace-req-1"));

        JsonNode access = accessLog(output, "trace-req-1").orElseThrow();
        assertThat(access.path("traceId").asText()).matches("^[0-9a-f]{32}$");
        assertThat(access.path("spanId").asText()).matches("^[0-9a-f]{16}$");
        assertThat(access.path("requestId").asText()).isEqualTo("trace-req-1");
    }

    @Test
    void incomingTraceparentIsContinued(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/info").header("X-Request-Id", "trace-req-2")
                .header("traceparent", "00-" + TRACE_ID + "-" + PARENT_SPAN + "-01"));

        JsonNode access = accessLog(output, "trace-req-2").orElseThrow();
        assertThat(access.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(access.path("spanId").asText()).matches("^[0-9a-f]{16}$").isNotEqualTo(PARENT_SPAN);
    }

    @Test
    void errorEventsCarryExceptionAndErrorCodeAsFields(CapturedOutput output) throws Exception {
        log.error("Unexpected error", kv("errorCode", "INTERNAL_ERROR"), kv("status", 500),
                new IllegalStateException("boom"));

        JsonNode error = output.getOut().lines().filter(line -> line.contains("\"errorCode\":\"INTERNAL_ERROR\""))
                .map(this::parse).flatMap(Optional::stream)
                .filter(node -> node.path("logger").asText().equals(TraceCorrelationTest.class.getName()))
                .findFirst().orElseThrow();
        assertThat(error.path("level").asText()).isEqualTo("ERROR");
        assertThat(error.path("status").asInt()).isEqualTo(500);
        assertThat(error.path("exception").asText()).contains("IllegalStateException").contains("boom");
    }

    @Test
    void logstashAppenderIsAbsentWhenDisabled() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<Object> appenders = new java.util.ArrayList<>();
        context.getLoggerList().forEach(logger -> logger.iteratorForAppenders().forEachRemaining(appenders::add));

        assertThat(appenders).isNotEmpty().noneMatch(LogstashTcpSocketAppender.class::isInstance);
    }

    private Optional<JsonNode> accessLog(CapturedOutput output, String requestId) {
        return output.getOut().lines()
                .filter(line -> line.contains(requestId) && line.contains("com.lebane.access"))
                .map(this::parse).flatMap(Optional::stream)
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
