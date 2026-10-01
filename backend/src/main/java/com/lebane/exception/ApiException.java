package com.lebane.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de aplicación esperado, con status HTTP, código estable y mensaje seguro para el cliente. El mensaje nunca
 * debe contener datos internos ni datos personales.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    protected ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
