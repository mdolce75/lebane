package com.lebane.exception;

import org.springframework.http.HttpStatus;

/** La operación es válida sintácticamente pero contradice el estado actual del recurso (HTTP 409). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
