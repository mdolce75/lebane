package com.lebane.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import ch.qos.logback.core.status.StatusManager;

/**
 * Reemplaza la impresión en texto plano de los estados internos de Logback por eventos JSON estructurados.
 *
 * <p>Spring Boot registra un {@link OnConsoleStatusListener} antes de procesar logback-spring.xml, que imprime en
 * stdout, como texto y con stack trace, los WARN internos de Logback; por ejemplo, cada intento fallido de conexión
 * del appender de Logstash. Eso rompe el stream JSON. Este listener, declarado en logback-spring.xml:
 * <ul>
 *   <li>retira los listeners de consola al arrancar;</li>
 *   <li>reemite los estados WARN/ERROR por el logger {@value #LOGGER_NAME}, que solo escribe en CONSOLE (sin
 *       additivity: nunca hacia Logstash, para evitar bucles);</li>
 *   <li>limita a un evento por origen cada {@value #WINDOW_MS} ms: una caída de Logstash queda visible como
 *       dependencia degradada sin inundar los logs.</li>
 * </ul>
 */
public class StructuredStatusListener extends ContextAwareBase implements StatusListener, LifeCycle {

    public static final String LOGGER_NAME = "com.lebane.logging.logback";
    static final long WINDOW_MS = 60_000;

    private final Map<String, Long> lastEmissionByOrigin = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile boolean started;

    public StructuredStatusListener() {
        this(System::currentTimeMillis);
    }

    StructuredStatusListener(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void start() {
        StatusManager statusManager = getContext().getStatusManager();
        for (StatusListener listener : statusManager.getCopyOfStatusListenerList()) {
            if (listener instanceof OnConsoleStatusListener) {
                statusManager.remove(listener);
            }
        }
        started = true;
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isResetResistant() {
        return false;
    }

    @Override
    public void addStatusEvent(Status status) {
        if (!started || status.getEffectiveLevel() < Status.WARN) {
            return;
        }
        String origin = describe(status.getOrigin());
        long now = clock.getAsLong();
        Long last = lastEmissionByOrigin.get(origin);
        if (last != null && now - last < WINDOW_MS) {
            return;
        }
        lastEmissionByOrigin.put(origin, now);

        Logger logger = ((LoggerContext) getContext()).getLogger(LOGGER_NAME);
        Throwable cause = status.getThrowable();
        String causeText = cause == null ? null : cause.getClass().getName() + ": " + cause.getMessage();
        if (status.getLevel() >= Status.ERROR) {
            logger.error("Logback internal status: {}", status.getMessage(), kv("logbackOrigin", origin),
                    kv("cause", causeText));
        } else {
            logger.warn("Logback internal status: {}", status.getMessage(), kv("logbackOrigin", origin),
                    kv("cause", causeText));
        }
    }

    private static String describe(Object origin) {
        if (origin == null) {
            return "unknown";
        }
        if (origin instanceof Appender<?> appender) {
            return origin.getClass().getSimpleName() + "[" + appender.getName() + "]";
        }
        return origin.getClass().getSimpleName();
    }
}
