package com.lebane.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registra los cambios de estado de liveness/readiness (eventos de ciclo de vida relevantes para operación).
 */
@Component
public class AvailabilityStateLogger {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityStateLogger.class);

    @EventListener
    public void onLivenessChange(AvailabilityChangeEvent<LivenessState> event) {
        if (event.getState() == LivenessState.BROKEN) {
            log.error("Liveness state changed to {}", kv("livenessState", event.getState()));
        } else {
            log.info("Liveness state changed to {}", kv("livenessState", event.getState()));
        }
    }

    @EventListener
    public void onReadinessChange(AvailabilityChangeEvent<ReadinessState> event) {
        if (event.getState() == ReadinessState.REFUSING_TRAFFIC) {
            log.warn("Readiness state changed to {}", kv("readinessState", event.getState()));
        } else {
            log.info("Readiness state changed to {}", kv("readinessState", event.getState()));
        }
    }
}
