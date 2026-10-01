package com.lebane.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * PostgreSQL real para tests de integración ({@code @ImportTestcontainers(PostgresContainer.class)}). El contenedor
 * es estático: se inicia una sola vez por JVM y lo comparten todos los contextos. Los tests no deben asumir una base
 * vacía (usan datos propios y comparan contra ellos, no contra totales globales).
 */
public interface PostgresContainer {

    @Container
    @ServiceConnection
    PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
