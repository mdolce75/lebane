package com.lebane.storage.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.lebane.exception.DependencyUnavailableException;

/**
 * Prepara el bucket al arrancar, en segundo plano: si MinIO no está disponible la aplicación arranca igual (el
 * listado y el detalle no dependen de MinIO) y el bucket se crea antes de la primera subida.
 */
@Component
public class StorageBucketInitializer {

    private static final Logger log = LoggerFactory.getLogger(StorageBucketInitializer.class);

    private final ObjectStorageService storage;

    public StorageBucketInitializer(ObjectStorageService storage) {
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("storage-bucket-init").start(() -> {
            try {
                storage.ensureBucket();
                log.info("Storage bucket ready", kv("bucket", storage.bucket()));
            } catch (DependencyUnavailableException e) {
                log.warn("Storage not available at startup; bucket will be prepared on first upload",
                        kv("bucket", storage.bucket()));
            }
        });
    }
}
