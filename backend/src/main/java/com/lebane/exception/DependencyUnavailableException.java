package com.lebane.exception;

import org.springframework.http.HttpStatus;

/**
 * Una dependencia externa necesaria para la operación no está disponible (caída, timeout, circuito abierto o mal
 * configurada). HTTP 503: el cliente puede reintentar más tarde. El detalle técnico queda en los logs.
 */
public class DependencyUnavailableException extends ApiException {

    public DependencyUnavailableException(ErrorCode errorCode, String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
        initCause(cause);
    }
}
