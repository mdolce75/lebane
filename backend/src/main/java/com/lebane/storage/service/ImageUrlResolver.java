package com.lebane.storage.service;

/**
 * Traduce la clave de un objeto del storage a la URL que consume el navegador. Abstrae la estrategia (bucket público,
 * URL firmada, CDN) del dominio: los DTOs nunca construyen URLs por su cuenta.
 */
public interface ImageUrlResolver {

    String urlFor(String objectKey);
}
