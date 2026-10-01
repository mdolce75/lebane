package com.lebane.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String recurso) {
        super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "No se encontró el " + recurso + " solicitado");
    }
}
