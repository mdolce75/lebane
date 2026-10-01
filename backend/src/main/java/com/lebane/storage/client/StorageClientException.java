package com.lebane.storage.client;

/**
 * Fallo permanente del storage (credenciales inválidas, acceso denegado, bucket inexistente, respuesta inválida):
 * reintentar no lo resuelve y no abre el circuito. Indica un problema de configuración. El mensaje nunca incluye
 * credenciales ni URLs firmadas.
 */
public class StorageClientException extends RuntimeException {

    private final String errorCode;

    public StorageClientException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Código de error del proveedor (p. ej. {@code AccessDenied}) o el tipo de la excepción original. */
    public String getErrorCode() {
        return errorCode;
    }
}
