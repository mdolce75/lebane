package com.lebane.resilience;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * {@link ResilientExecutor} con la misma política que producción (mismos predicados) pero tiempos cortos, para
 * tests unitarios rápidos: 3 intentos con 10 ms de espera, timeout de 200 ms, circuito que abre con 4 llamadas y 50 %
 * de fallos.
 */
public final class TestResilience {

    public static final Duration TIMEOUT = Duration.ofMillis(200);

    private TestResilience() {
    }

    public static ResilientExecutor executor() {
        TransientFailurePredicate transientFailure = new TransientFailurePredicate();
        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(transientFailure)
                .build());
        RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(transientFailure)
                .build());
        TimeLimiterRegistry timeLimiters = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(TIMEOUT)
                .cancelRunningFuture(true)
                .build());
        return new ResilientExecutor(circuitBreakers, retries, timeLimiters, new ResilienceEventLogger());
    }
}
