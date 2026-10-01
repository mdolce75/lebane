package com.lebane.exception;

/**
 * Códigos de error estables expuestos en {@link ApiError#error()} y en el campo de log {@code errorCode}.
 * Se amplía en la Fase 2 con los códigos de dominio.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    CONFLICT,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR
}
