package com.lebane.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auditoría básica ({@code created_at} / {@code updated_at}). En una clase propia, y no en la clase principal, para
 * que los tests de slice web ({@code @WebMvcTest}) no requieran infraestructura JPA.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaConfig {
}
