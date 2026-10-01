package com.lebane.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Error de aplicación esperado, con status HTTP, código estable y mensaje seguro para el cliente. El mensaje nunca
 * debe contener datos internos ni datos personales.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final Map<String, String> fieldErrors;

    protected ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }

    protected ApiException(HttpStatus status, ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
