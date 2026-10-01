package com.lebane.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.WarnStatus;

class StructuredStatusListenerTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private LoggerContext context;
    private ListAppender<ILoggingEvent> captured;
    private StructuredStatusListener listener;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        captured = new ListAppender<>();
        captured.setContext(context);
        captured.start();
        context.getLogger(StructuredStatusListener.LOGGER_NAME).addAppender(captured);

        listener = new StructuredStatusListener(now::get);
        listener.setContext(context);
    }

    @Test
    void removesConsoleListenersOnStart() {
        OnConsoleStatusListener console = new OnConsoleStatusListener();
        context.getStatusManager().add(console);

        listener.start();

        assertThat(context.getStatusManager().getCopyOfStatusListenerList()).doesNotContain(console);
    }

    @Test
    void reemitsWarningsAsStructuredEventsWithoutStackTrace() {
        listener.start();

        listener.addStatusEvent(new WarnStatus("connection failed", this, new UnknownHostException("logstash")));

        assertThat(captured.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("connection failed");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getArgumentArray()).extracting(String::valueOf)
                    .anySatisfy(arg -> assertThat(arg).contains("UnknownHostException: logstash"));
        });
    }

    @Test
    void ignoresInfoStatuses() {
        listener.start();

        listener.addStatusEvent(new InfoStatus("configured", this));

        assertThat(captured.list).isEmpty();
    }

    @Test
    void rateLimitsRepeatedWarningsPerOrigin() {
        listener.start();

        listener.addStatusEvent(new WarnStatus("connection failed", this));
        listener.addStatusEvent(new WarnStatus("waiting before reconnection", this));
        assertThat(captured.list).hasSize(1);

        now.addAndGet(StructuredStatusListener.WINDOW_MS);
        listener.addStatusEvent(new WarnStatus("connection failed", this));
        assertThat(captured.list).hasSize(2);
    }

    @Test
    void ignoresStatusesBeforeStart() {
        listener.addStatusEvent(new WarnStatus("too early", this));

        assertThat(captured.list).isEmpty();
    }
}
