package com.lebane.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El appender de Logstash de producción ({@code logback-logstash-true.xml}) en un LoggerContext aislado:
 * <ul>
 *   <li>con Logstash disponible, envía un JSON por evento con los campos comunes, el MDC y los argumentos
 *       estructurados, sin propiedades internas del contexto y con los secretos enmascarados;</li>
 *   <li>con Logstash caído, loguear no bloquea al hilo que llama (buffer asíncrono, descarte sin espera).</li>
 * </ul>
 * (En la aplicación, Logback se configura una sola vez por JVM; por eso este test arma su propio contexto.)
 */
class LogstashAppenderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private LoggerContext context;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void sendsStructuredMaskedJsonEventsToLogstash() throws Exception {
        try (ServerSocket logstash = new ServerSocket(0)) {
            logstash.setSoTimeout(10_000);
            Logger logger = configure(logstash.getLocalPort());

            MDC.put(RequestContext.REQUEST_ID_MDC_KEY, "ls-req-1");
            MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
            logger.warn("Circuit breaker opened with password=SuperSecret123", kv("circuitBreaker", "storage"),
                    kv("provider", "minio"), kv("secretKey", "AnotherSecret456"));

            List<JsonNode> events = read(logstash, 1);
            JsonNode event = events.getFirst();
            assertThat(event.path("@timestamp").asText()).isNotBlank();
            assertThat(event.path("level").asText()).isEqualTo("WARN");
            assertThat(event.path("logger").asText()).isEqualTo("logstash.test");
            assertThat(event.path("service").asText()).isEqualTo("lebane-test");
            assertThat(event.path("environment").asText()).isEqualTo("test");
            assertThat(event.path("requestId").asText()).isEqualTo("ls-req-1");
            assertThat(event.path("traceId").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(event.path("circuitBreaker").asText()).isEqualTo("storage");
            assertThat(event.path("provider").asText()).isEqualTo("minio");
            // Sin propiedades internas del LoggerContext y con secretos enmascarados.
            assertThat(event.has("LOGSTASH_HOST")).isFalse();
            assertThat(event.toString()).doesNotContain("SuperSecret123", "AnotherSecret456");
        }
    }

    @Test
    void unavailableLogstashNeverBlocksTheCaller() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort(); // se cierra: nadie escucha en este puerto
        }
        Logger logger = configure(closedPort);

        long start = System.nanoTime();
        for (int i = 0; i < 20_000; i++) {
            logger.info("Evento {}", i, kv("requestId", "r-" + i));
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // 20.000 eventos sin destino: se encolan o se descartan, nunca se espera a la red.
        assertThat(elapsedMs).isLessThan(3_000);

        // Apagar con eventos pendientes no demora más que shutdownGracePeriod (5 s), no el minuto por defecto.
        long stopStart = System.nanoTime();
        context.stop();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStart)).isLessThan(8_000);
    }

    private Logger configure(int port) throws Exception {
        context = new LoggerContext();
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.putProperty("LOGSTASH_HOST", "127.0.0.1");
        context.putProperty("LOGSTASH_PORT", String.valueOf(port));
        context.putProperty("LOG_CUSTOM_FIELDS",
                "{\"service\":\"lebane-test\",\"application\":\"lebane-backend\",\"environment\":\"test\"}");
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-logstash-test.xml"));
        return context.getLogger("logstash.test");
    }

    private List<JsonNode> read(ServerSocket server, int count) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        try (Socket socket = server.accept();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            while (events.size() < count) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                events.add(mapper.readTree(line));
            }
        }
        return events;
    }
}
