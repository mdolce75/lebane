package com.lebane.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Validación hecha en el servicio (p. ej. el contenido real de un archivo), con el mismo formato que Bean Validation. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String campo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, GlobalExceptionHandler.VALIDATION_MESSAGE,
                Map.of(campo, mensaje));
    }
}
