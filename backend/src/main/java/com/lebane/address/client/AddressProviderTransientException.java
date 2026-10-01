package com.lebane.address.client;

import com.lebane.resilience.TransientFailure;

/** Fallo transitorio del proveedor (red, 5xx, 429): se reintenta y cuenta para el circuit breaker. */
public class AddressProviderTransientException extends AddressProviderException implements TransientFailure {

    public AddressProviderTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
