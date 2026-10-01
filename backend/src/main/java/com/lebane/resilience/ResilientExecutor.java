package com.lebane.resilience;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;

/**
 * Ejecuta llamadas a dependencias externas con la política de resiliencia de su instancia
 * ({@code resilience4j.*.instances.<instancia>} en {@code application.yml}):
 *
 * <pre>
 *   Retry ( CircuitBreaker ( TimeLimiter ( llamada ) ) )
 * </pre>
 *
 * <ul>
 *   <li>Cada intento pasa por el circuit breaker: con el circuito abierto se rechaza sin tocar la red
 *       ({@code CallNotPermittedException}, no se reintenta).</li>
 *   <li>Cada intento tiene su propio timeout; al vencer se cancela (interrumpe) la llamada.</li>
 *   <li>Solo se reintentan fallos transitorios ({@link TransientFailurePredicate}).</li>
 * </ul>
 *
 * La llamada corre en un virtual thread (el TimeLimiter necesita un future) con el contexto del request
 * propagado: MDC (requestId, traceId) y la observación de Micrometer (propagación del trace a la llamada saliente).
 * Las excepciones se relanzan sin envolver; el fallback lo decide quien llama, que conoce la semántica.
 */
@Component
public class ResilientExecutor implements DisposableBean {

    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final TimeLimiterRegistry timeLimiters;
    private final ResilienceEventLogger eventLogger;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ContextSnapshotFactory snapshots = ContextSnapshotFactory.builder().build();
    private final Map<String, Policy> policies = new ConcurrentHashMap<>();

    public ResilientExecutor(CircuitBreakerRegistry circuitBreakers, RetryRegistry retries,
            TimeLimiterRegistry timeLimiters, ResilienceEventLogger eventLogger) {
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.timeLimiters = timeLimiters;
        this.eventLogger = eventLogger;
    }

    /**
     * @param instance nombre de la instancia de Resilience4j (configuración, métricas y logs)
     * @param provider proveedor concreto detrás de la instancia (p. ej. {@code minio}, {@code georef}), para logs
     */
    public <T> T execute(String instance, String provider, Callable<T> call) throws Exception {
        Policy policy = policies.computeIfAbsent(instance, name -> createPolicy(name, provider));
        Callable<T> attempt = withRequestContext(call);
        Callable<T> timed = () -> policy.timeLimiter().executeFutureSupplier(() -> executor.submit(attempt));
        Callable<T> guarded = CircuitBreaker.decorateCallable(policy.circuitBreaker(), timed);
        return Retry.decorateCallable(policy.retry(), guarded).call();
    }

    /** Estado actual del circuito (para logs y fallbacks). */
    public CircuitBreaker.State state(String instance) {
        return circuitBreakers.circuitBreaker(instance).getState();
    }

    private Policy createPolicy(String instance, String provider) {
        Policy policy = new Policy(circuitBreakers.circuitBreaker(instance), retries.retry(instance),
                timeLimiters.timeLimiter(instance));
        eventLogger.attach(policy.circuitBreaker(), policy.retry(), policy.timeLimiter(), provider);
        return policy;
    }

    private <T> Callable<T> withRequestContext(Callable<T> call) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        ContextSnapshot snapshot = snapshots.captureAll();
        return () -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                return call.call();
            } finally {
                MDC.clear();
            }
        };
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private record Policy(CircuitBreaker circuitBreaker, Retry retry, TimeLimiter timeLimiter) {
    }
}
