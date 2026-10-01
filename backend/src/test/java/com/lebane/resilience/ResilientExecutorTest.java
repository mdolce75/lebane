package com.lebane.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import net.logstash.logback.argument.StructuredArgument;

class ResilientExecutorTest {

    private ResilientExecutor executor;
    private ListAppender<ILoggingEvent> events;
    private Logger resilienceLogger;

    @BeforeEach
    void setUp() {
        executor = TestResilience.executor();
        resilienceLogger = (Logger) LoggerFactory.getLogger(ResilienceEventLogger.LOGGER_NAME);
        events = new ListAppender<>();
        events.start();
        resilienceLogger.addAppender(events);
        resilienceLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        resilienceLogger.detachAppender(events);
        resilienceLogger.setLevel(null);
        executor.destroy();
        MDC.clear();
    }

    @Test
    void returnsTheResultAndPropagatesTheRequestContext() throws Exception {
        MDC.put("requestId", "req-ctx-1");

        String result = executor.execute("svc", "prov", () -> Thread.currentThread().isVirtual()
                + ":" + MDC.get("requestId"));

        assertThat(result).isEqualTo("true:req-ctx-1");
    }

    @Test
    void retriesTransientFailuresAndLogsExhaustion() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("svc", "prov", () -> {
            attempts.incrementAndGet();
            throw new IOException("connection refused");
        })).isInstanceOf(IOException.class);

        assertThat(attempts).hasValue(3);
        assertThat(messages()).contains("Retrying call", "Retries exhausted");
        ILoggingEvent exhausted = event("Retries exhausted");
        assertThat(exhausted.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(exhausted)).containsEntry("circuitBreaker", "svc").containsEntry("provider", "prov")
                .containsEntry("attempts", "3").containsEntry("cause", "IOException");
    }

    @Test
    void succeedsAfterATransientFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute("svc", "prov", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("blip");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(messages()).contains("Call succeeded after retrying");
    }

    @Test
    void doesNotRetryOrOpenTheCircuitOnPermanentFailures() {
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> executor.execute("svc", "prov", () -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("bad request");
            })).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(attempts).hasValue(6);
        assertThat(executor.state("svc")).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void timesOutAndCancelsSlowCalls() {
        AtomicBoolean interrupted = new AtomicBoolean();
        long start = System.nanoTime();

        assertThatThrownBy(() -> executor.execute("slow", "prov", () -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
            return "never";
        })).isInstanceOf(TimeoutException.class);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 3 intentos de 200 ms + esperas: muy lejos de los 5 s de la llamada.
        assertThat(elapsedMs).isLessThan(2_000);
        assertThat(messages()).contains("Call timed out");
        assertThat(fields(event("Call timed out"))).containsEntry("timeoutMs", "200");
        awaitTrue(interrupted);
    }

    @Test
    void opensTheCircuitAndRejectsCallsWithoutReachingTheDependency() {
        AtomicInteger calls = new AtomicInteger();
        // 2 operaciones x 3 intentos = 6 fallos: con ventana de 4 y 50 %, el circuito abre.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> executor.execute("cb", "prov", () -> {
                calls.incrementAndGet();
                throw new IOException("down");
            })).isInstanceOfAny(IOException.class, CallNotPermittedException.class);
        }
        int callsWhenOpen = calls.get();

        assertThatThrownBy(() -> executor.execute("cb", "prov", () -> {
            calls.incrementAndGet();
            return "x";
        })).isInstanceOf(CallNotPermittedException.class);

        assertThat(executor.state("cb")).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(calls).hasValue(callsWhenOpen);
        ILoggingEvent opened = event("Circuit breaker opened: dependency degraded");
        assertThat(opened.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(opened)).containsEntry("fromState", "CLOSED").containsEntry("toState", "OPEN")
                .containsEntry("circuitBreaker", "cb").containsEntry("provider", "prov");
        assertThat(messages()).contains("Call rejected: circuit breaker is open");
    }

    @Test
    void neverLogsExceptionMessages() {
        assertThatThrownBy(() -> executor.execute("svc", "prov", () -> {
            throw new IOException("http://user:s3cret@host/path?apiKey=XYZ");
        })).isInstanceOf(IOException.class);

        assertThat(events.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("s3cret", "apiKey");
            assertThat(event.getArgumentArray()).extracting(String::valueOf)
                    .noneMatch(arg -> arg.contains("s3cret") || arg.contains("apiKey"));
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private List<String> messages() {
        return events.list.stream().map(ILoggingEvent::getMessage).toList();
    }

    private ILoggingEvent event(String message) {
        return events.list.stream().filter(e -> e.getMessage().equals(message)).findFirst().orElseThrow();
    }

    /** Campos estructurados del evento ({@code kv}) como mapa clave → valor. */
    private static Map<String, String> fields(ILoggingEvent event) {
        Map<String, String> fields = new java.util.HashMap<>();
        for (Object argument : event.getArgumentArray()) {
            if (argument instanceof StructuredArgument structured) {
                String[] keyValue = structured.toString().split("=", 2);
                fields.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : null);
            }
        }
        return fields;
    }

    private static void awaitTrue(AtomicBoolean flag) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!flag.get() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(flag).as("la llamada lenta fue interrumpida al vencer el timeout").isTrue();
    }
}
