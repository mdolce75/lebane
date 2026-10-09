package com.lebane.address;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.lebane.address.service.AddressAutocompleteService;
import com.lebane.resilience.ResilientExecutor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Proveedor externo real (cliente Georef) contra un servidor local, con la configuración de Resilience4j de
 * {@code application.yml}: respuestas normales, caída del proveedor con fallback degradado, apertura del circuito,
 * rechazo sin red y recuperación (HALF_OPEN → CLOSED) cuando el proveedor vuelve. No requiere base de datos.
 */
@SpringBootTest(properties = {
        "lebane.address.provider=external",
        "R4J_RETRY_WAIT=20ms",
        "resilience4j.circuitbreaker.instances.address.wait-duration-in-open-state=1s"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("nodb")
@ExtendWith(OutputCaptureExtension.class)
class AddressResilienceIT {

    private static final String URL = "/api/direcciones/autocompletar";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final FakeGeorefServer GEOREF = startServer();

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("lebane.address.url", GEOREF::url);
    }

    @AfterAll
    static void stopServer() {
        GEOREF.close();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ResilientExecutor resilience;

    @Test
    void degradesOpensTheCircuitAndRecovers(CapturedOutput output) throws Exception {
        GEOREF.reset();
        mockMvc.perform(get(URL).param("q", "Libertador 4850").header("X-Request-Id", "addr-it-1")
                        .header("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("georef"))
                .andExpect(jsonPath("$.degradado").value(false))
                .andExpect(jsonPath("$.sugerencias[0].calle").value("Av. Del Libertador"));
        // Correlación hacia el proveedor: el mismo requestId y la misma traza (nuevo span hijo).
        assertThat(GEOREF.requestIds()).contains("addr-it-1");
        assertThat(GEOREF.traceparents()).singleElement().satisfies(traceparent ->
                assertThat(traceparent).startsWith("00-" + TRACE_ID + "-").doesNotContain("00f067aa0ba902b7"));

        // Proveedor caído: 2 intentos por consulta; con 3 consultas (6 fallos) el circuito se abre.
        GEOREF.respond(503, "{}");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(URL).param("q", "Gorriti"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.degradado").value(true))
                    .andExpect(jsonPath("$.sugerencias").isEmpty());
        }
        assertThat(resilience.state(AddressAutocompleteService.INSTANCE)).isEqualTo(CircuitBreaker.State.OPEN);

        // Con el circuito abierto no se llama al proveedor.
        int llamadasAntes = GEOREF.queries().size();
        mockMvc.perform(get(URL).param("q", "Gorriti")).andExpect(jsonPath("$.degradado").value(true));
        assertThat(GEOREF.queries()).hasSize(llamadasAntes);

        // El proveedor vuelve; pasado el tiempo de espera, HALF_OPEN deja pasar llamadas de prueba y cierra.
        GEOREF.respond(200, FakeGeorefServer.RESPUESTA_OK);
        Thread.sleep(1_100);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(URL).param("q", "Libertador 4850"))
                    .andExpect(jsonPath("$.degradado").value(false));
        }
        assertThat(resilience.state(AddressAutocompleteService.INSTANCE)).isEqualTo(CircuitBreaker.State.CLOSED);

        assertThat(output.getOut()).contains(
                "Address provider unavailable: fallback to manual entry",
                "Circuit breaker opened: dependency degraded",
                "Call rejected: circuit breaker is open",
                "Circuit breaker closed: dependency recovered",
                "\"circuitBreaker\":\"address\"", "\"provider\":\"georef\"", "\"fallback\":true");
    }

    private static FakeGeorefServer startServer() {
        try {
            return new FakeGeorefServer();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
