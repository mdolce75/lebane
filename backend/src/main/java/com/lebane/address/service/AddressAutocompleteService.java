package com.lebane.address.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lebane.address.dto.AutocompleteResponse;
import com.lebane.address.dto.SugerenciaDireccion;
import com.lebane.address.provider.AddressProperties;
import com.lebane.address.provider.AddressProvider;
import com.lebane.resilience.ResilientExecutor;
import com.lebane.resilience.TransientFailurePredicate;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Autocompletado de direcciones con resiliencia (instancia {@code address}).
 *
 * <p>Fallback: el autocompletado es una ayuda, no un requisito (el formulario admite cargar la dirección a mano).
 * Si el proveedor falla, tarda o el circuito está abierto, se responde 200 con {@code degradado: true} y sin
 * sugerencias. Nunca se devuelven datos de otro origen como si fueran del proveedor.
 *
 * <p>Logs: fallback por fallo transitorio o circuito abierto en WARN; por fallo permanente (credenciales, respuesta
 * inválida) en ERROR, porque requiere intervención. Métricas: {@code lebane.address.autocomplete} por proveedor y
 * resultado ({@code success} | {@code fallback}).
 */
@Service
public class AddressAutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(AddressAutocompleteService.class);
    public static final String INSTANCE = "address";
    static final String METRIC = "lebane.address.autocomplete";

    private final AddressProvider provider;
    private final ResilientExecutor resilience;
    private final AddressProperties properties;
    private final MeterRegistry meterRegistry;
    private final TransientFailurePredicate transientFailure = new TransientFailurePredicate();

    public AddressAutocompleteService(AddressProvider provider, ResilientExecutor resilience,
            AddressProperties properties, MeterRegistry meterRegistry) {
        this.provider = provider;
        this.resilience = resilience;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /** @param limite máximo pedido por el cliente; se acota a {@code ADDRESS_PROVIDER_MAX_RESULTS} */
    public AutocompleteResponse autocompletar(String texto, Integer limite) {
        int max = limite == null ? properties.maxResults() : Math.min(limite, properties.maxResults());
        long start = System.nanoTime();
        try {
            List<SugerenciaDireccion> sugerencias =
                    resilience.execute(INSTANCE, provider.nombre(), () -> provider.buscar(texto.strip(), max));
            record("success", start);
            return AutocompleteResponse.ok(sugerencias, provider.nombre());
        } catch (Exception e) {
            record("fallback", start);
            boolean degradacionEsperable = e instanceof CallNotPermittedException || transientFailure.test(e);
            Object[] fields = {kv("circuitBreaker", INSTANCE), kv("provider", provider.nombre()),
                    kv("fallback", true), kv("state", resilience.state(INSTANCE)),
                    kv("cause", e.getClass().getSimpleName()), kv("durationMs", elapsedMs(start))};
            if (degradacionEsperable) {
                log.warn("Address provider unavailable: fallback to manual entry", fields);
            } else {
                log.error("Address provider failed (non-transient): fallback to manual entry", fields);
            }
            return AutocompleteResponse.degradado(provider.nombre());
        }
    }

    private void record(String outcome, long start) {
        Timer.builder(METRIC)
                .description("Consultas de autocompletado de direcciones")
                .tag("provider", provider.nombre())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
