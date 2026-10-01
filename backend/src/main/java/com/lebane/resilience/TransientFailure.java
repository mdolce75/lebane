package com.lebane.resilience;

/**
 * Marca una excepción de una integración externa como transitoria (caída de red, timeout, error 5xx, throttling):
 * reintentarla tiene sentido y cuenta como fallo para el circuit breaker. Los errores permanentes (credenciales
 * inválidas, request inválido, 4xx) no la implementan: no se reintentan ni abren el circuito.
 */
public interface TransientFailure {
}
