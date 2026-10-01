package com.lebane.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lebane.address.client.AddressProviderException;
import com.lebane.address.client.AddressProviderTransientException;
import com.lebane.address.dto.AutocompleteResponse;
import com.lebane.address.dto.SugerenciaDireccion;
import com.lebane.address.provider.AddressProperties;
import com.lebane.address.provider.AddressProvider;
import com.lebane.address.service.AddressAutocompleteService;
import com.lebane.resilience.ResilientExecutor;
import com.lebane.resilience.TestResilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AddressAutocompleteServiceTest {

    private static final SugerenciaDireccion SUGERENCIA = new SugerenciaDireccion("Gorriti", "4850", "CABA", "CABA",
            null, null, "georef:1:4850", "GORRITI 4850");

    private final AddressProvider provider = mock(AddressProvider.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private ResilientExecutor resilience;
    private AddressAutocompleteService service;
    private ListAppender<ILoggingEvent> logs;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        when(provider.nombre()).thenReturn("georef");
        resilience = TestResilience.executor();
        service = new AddressAutocompleteService(provider, resilience,
                new AddressProperties("external", "http://x", null, Duration.ofSeconds(1), 5), meters);
        serviceLogger = (Logger) LoggerFactory.getLogger(AddressAutocompleteService.class);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
        resilience.destroy();
    }

    @Test
    void returnsProviderSuggestions() throws Exception {
        when(provider.buscar("Gorriti 4850", 5)).thenReturn(List.of(SUGERENCIA));

        AutocompleteResponse response = service.autocompletar("  Gorriti 4850 ", null);

        assertThat(response.sugerencias()).containsExactly(SUGERENCIA);
        assertThat(response.degradado()).isFalse();
        assertThat(response.proveedor()).isEqualTo("georef");
        assertThat(response.mensaje()).isNull();
        assertThat(meters.get("lebane.address.autocomplete").tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void capsTheRequestedLimit() throws Exception {
        when(provider.buscar(anyString(), anyInt())).thenReturn(List.of());

        service.autocompletar("Gorriti", 50);
        service.autocompletar("Gorriti", 2);

        verify(provider).buscar("Gorriti", 5);
        verify(provider).buscar("Gorriti", 2);
    }

    @Test
    void transientFailuresDegradeAfterRetryingWithWarn() throws Exception {
        when(provider.buscar(anyString(), anyInt())).thenThrow(new AddressProviderTransientException("down", null));

        AutocompleteResponse response = service.autocompletar("Gorriti", null);

        assertThat(response.degradado()).isTrue();
        assertThat(response.sugerencias()).isEmpty();
        assertThat(response.mensaje()).contains("ingresá la dirección manualmente");
        verify(provider, times(3)).buscar(anyString(), anyInt());
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getMessage()).contains("fallback");
        });
        assertThat(meters.get("lebane.address.autocomplete").tag("outcome", "fallback").timer().count())
                .isEqualTo(1);
    }

    @Test
    void permanentFailuresDegradeWithoutRetryAndLogError() throws Exception {
        when(provider.buscar(anyString(), anyInt())).thenThrow(new AddressProviderException("401", null));

        AutocompleteResponse response = service.autocompletar("Gorriti", null);

        assertThat(response.degradado()).isTrue();
        verify(provider, times(1)).buscar(anyString(), anyInt());
        assertThat(logs.list).singleElement().extracting(ILoggingEvent::getLevel).isEqualTo(Level.ERROR);
    }

    @Test
    void openCircuitDegradesWithoutCallingTheProvider() throws Exception {
        when(provider.buscar(anyString(), anyInt())).thenThrow(new IOException("down"));
        service.autocompletar("Gorriti", null);
        service.autocompletar("Gorriti", null);
        assertThat(resilience.state(AddressAutocompleteService.INSTANCE)).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = org.mockito.Mockito.mockingDetails(provider).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("buscar")).toList().size();

        AutocompleteResponse response = service.autocompletar("Gorriti", null);

        assertThat(response.degradado()).isTrue();
        verify(provider, times(callsBefore)).buscar(eq("Gorriti"), anyInt());
    }
}
