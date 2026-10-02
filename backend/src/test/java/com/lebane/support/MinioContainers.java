package com.lebane.support;

import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MinIO para tests de integración, con la misma imagen que {@code docker-compose.yml} (las imágenes oficiales
 * {@code minio/minio} ya no se pueden descargar; ver docs/decisiones.md). Es compatible con el módulo de Testcontainers.
 */
public final class MinioContainers {

    public static final String IMAGE =
            "cgr.dev/chainguard/minio@sha256:4692462f35d97d7e82c30371d82f057703c5d9489bcae726010594c812f2d285";
    public static final String ACCESS_KEY = "it-access-key";
    public static final String SECRET_KEY = "it-secret-key-0123456789";

    private MinioContainers() {
    }

    public static MinIOContainer create() {
        return new MinIOContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("minio/minio"))
                .withUserName(ACCESS_KEY)
                .withPassword(SECRET_KEY);
    }
}
