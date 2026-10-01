package com.lebane.exception;

import org.springframework.http.HttpStatus;

/** {@code If-Match} no coincide con la versión actual del recurso (HTTP 412). */
public class PreconditionFailedException extends ApiException {

    public PreconditionFailedException() {
        super(HttpStatus.PRECONDITION_FAILED, ErrorCode.PRECONDITION_FAILED,
                "El recurso fue modificado desde la última lectura; recargalo y volvé a intentar");
    }
}
