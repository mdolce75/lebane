package com.lebane.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * LOG_FORMAT y LOGSTASH_ENABLED eligen los archivos que incluye logback-spring.xml: un valor inválido debe impedir el
 * arranque en lugar de dejar la aplicación sin logs.
 */
class LoggingPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues("lebane.logging.service-name=lebane-backend", "lebane.logging.environment=test",
                    "lebane.logging.logstash.host=logstash", "lebane.logging.logstash.port=5000");

    @Test
    void acceptsSupportedValues() {
        runner.withPropertyValues("lebane.logging.format=text", "lebane.logging.logstash.enabled=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(LoggingProperties.class)
                        .extracting(LoggingProperties::format).isEqualTo("text"));
    }

    @Test
    void rejectsUnknownFormat() {
        runner.withPropertyValues("lebane.logging.format=xml", "lebane.logging.logstash.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("LOG_FORMAT"));
    }

    @Test
    void rejectsNonCanonicalLogstashFlag() {
        runner.withPropertyValues("lebane.logging.format=json", "lebane.logging.logstash.enabled=TRUE")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("LOGSTASH_ENABLED"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoggingProperties.class)
    static class Config {
    }
}
