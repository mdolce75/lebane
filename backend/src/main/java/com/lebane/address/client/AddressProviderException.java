package com.lebane.address.client;

/**
 * Fallo permanente del proveedor de direcciones (credenciales rechazadas, respuesta inválida): no se reintenta ni
 * abre el circuito. El mensaje nunca incluye la URL completa ni la API key.
 */
public class AddressProviderException extends RuntimeException {

    public AddressProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
