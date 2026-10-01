package com.lebane.logging;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * Claves y utilidades compartidas del correlation ID ({@code X-Request-Id}).
 */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";

    /**
     * Solo se aceptan IDs entrantes con caracteres seguros y longitud acotada: evita log injection
     * y que un cliente inyecte valores arbitrariamente grandes en todos los logs.
     */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    private RequestContext() {
    }

    public static boolean isValid(String candidate) {
        return candidate != null && VALID_REQUEST_ID.matcher(candidate).matches();
    }

    /** Devuelve el ID entrante si es válido; si no, genera uno nuevo. */
    public static String resolve(String incoming) {
        return isValid(incoming) ? incoming : generate();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** Request ID del hilo actual, si hay uno en el MDC. */
    public static Optional<String> currentRequestId() {
        return Optional.ofNullable(MDC.get(REQUEST_ID_MDC_KEY));
    }
}
