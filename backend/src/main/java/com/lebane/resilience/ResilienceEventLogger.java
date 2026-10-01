package com.lebane.resilience;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

/**
 * Logs estructurados de los eventos de Resilience4j. Cada evento lleva {@code circuitBreaker} (instancia) y
 * {@code provider}; {@code requestId}/{@code traceId} llegan por el MDC porque los eventos se emiten en el hilo del
 * request. Nunca se registra el mensaje de la excepción (puede contener URLs, hosts o API keys): solo su tipo.
 *
 * <ul>
 *   <li>WARN: circuito abierto, llamada rechazada por circuito abierto, reintentos agotados, timeout.</li>
 *   <li>INFO: reintento, transición a HALF_OPEN, recuperación (vuelta a CLOSED), éxito tras reintentar.</li>
 * </ul>
 */
@Component
public class ResilienceEventLogger {

    static final String LOGGER_NAME = "com.lebane.resilience";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    void attach(CircuitBreaker circuitBreaker, Retry retry, TimeLimiter timeLimiter, String provider) {
        String instance = circuitBreaker.getName();

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    CircuitBreaker.State from = event.getStateTransition().getFromState();
                    CircuitBreaker.State to = event.getStateTransition().getToState();
                    Object[] fields = {kv("circuitBreaker", instance), kv("provider", provider),
                            kv("fromState", from), kv("toState", to)};
                    if (to == CircuitBreaker.State.OPEN || to == CircuitBreaker.State.FORCED_OPEN) {
                        log.warn("Circuit breaker opened: dependency degraded", fields);
                    } else if (to == CircuitBreaker.State.CLOSED && from == CircuitBreaker.State.HALF_OPEN) {
                        log.info("Circuit breaker closed: dependency recovered", fields);
                    } else {
                        log.info("Circuit breaker state transition", fields);
                    }
                })
                .onCallNotPermitted(event -> log.warn("Call rejected: circuit breaker is open",
                        kv("circuitBreaker", instance), kv("provider", provider),
                        kv("state", circuitBreaker.getState())))
                .onError(event -> log.debug("Call failed", kv("circuitBreaker", instance), kv("provider", provider),
                        kv("durationMs", event.getElapsedDuration().toMillis()),
                        kv("cause", causeOf(event.getThrowable()))));

        retry.getEventPublisher()
                .onRetry(event -> log.info("Retrying call", kv("circuitBreaker", instance), kv("provider", provider),
                        kv("attempts", event.getNumberOfRetryAttempts()),
                        kv("waitMs", event.getWaitInterval().toMillis()),
                        kv("cause", causeOf(event.getLastThrowable()))))
                .onError(event -> log.warn("Retries exhausted", kv("circuitBreaker", instance),
                        kv("provider", provider), kv("attempts", event.getNumberOfRetryAttempts()),
                        kv("cause", causeOf(event.getLastThrowable()))))
                .onSuccess(event -> log.info("Call succeeded after retrying", kv("circuitBreaker", instance),
                        kv("provider", provider), kv("attempts", event.getNumberOfRetryAttempts())));

        timeLimiter.getEventPublisher()
                .onTimeout(event -> log.warn("Call timed out", kv("circuitBreaker", instance),
                        kv("provider", provider),
                        kv("timeoutMs", timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis())));
    }

    static String causeOf(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getSimpleName();
    }
}
