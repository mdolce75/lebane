package com.lebane.storage.client;

import com.lebane.resilience.TransientFailure;

/** Fallo transitorio del storage (red, 5xx, throttling): se reintenta y cuenta para el circuit breaker. */
public class StorageTransientException extends StorageClientException implements TransientFailure {

    public StorageTransientException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
