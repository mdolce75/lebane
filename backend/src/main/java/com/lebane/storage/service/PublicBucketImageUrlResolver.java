package com.lebane.storage.service;

import org.springframework.stereotype.Component;

import com.lebane.storage.config.StorageProperties;

/**
 * Estrategia de bucket con lectura pública: {@code <STORAGE_PUBLIC_URL>/<bucket>/<objectKey>}. Las claves de objeto
 * las genera el backend (sin datos del usuario), por lo que no requieren codificación adicional.
 */
@Component
public class PublicBucketImageUrlResolver implements ImageUrlResolver {

    private final String baseUrl;

    public PublicBucketImageUrlResolver(StorageProperties properties) {
        String publicUrl = properties.publicUrl().endsWith("/")
                ? properties.publicUrl().substring(0, properties.publicUrl().length() - 1)
                : properties.publicUrl();
        this.baseUrl = publicUrl + "/" + properties.bucket() + "/";
    }

    @Override
    public String urlFor(String objectKey) {
        return baseUrl + objectKey;
    }
}
