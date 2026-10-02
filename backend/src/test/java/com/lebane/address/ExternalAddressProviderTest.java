package com.lebane.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import com.lebane.address.client.AddressProviderException;
import com.lebane.address.client.AddressProviderTransientException;
import com.lebane.address.client.GeorefClient;
import com.lebane.address.dto.SugerenciaDireccion;
import com.lebane.address.provider.AddressProperties;
import com.lebane.address.provider.ExternalAddressProvider;
import com.lebane.resilience.TransientFailurePredicate;

/** Cliente Georef real contra un servidor HTTP local: mapeo, headers y clasificación de errores. */
class ExternalAddressProviderTest {

    // Holgado: el primer request de la JVM (inicialización del HttpClient, agente de cobertura) puede tardar
    // cientos de ms en una máquina cargada; con 500 ms el test fallaba de forma intermitente.
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private static FakeGeorefServer server;
    private ExternalAddressProvider provider;
    private final TransientFailurePredicate transientFailure = new TransientFailurePredicate();

    @BeforeAll
    static void startServer() throws Exception {
        server = new FakeGeorefServer();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void setUp() {
        server.reset();
        AddressProperties properties = new AddressProperties("external", server.url(), null,
                TIMEOUT, 5);
        provider = new ExternalAddressProvider(new GeorefClient(RestClient.builder(), properties));
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void mapsGeorefToTheApplicationModel() {
        MDC.put("requestId", "req-georef-1");

        List<SugerenciaDireccion> sugerencias = provider.buscar("Libertador 4850", 3);

        assertThat(sugerencias).singleElement().satisfies(s -> {
            assertThat(s.calle()).isEqualTo("Av. Del Libertador");
            assertThat(s.numero()).isEqualTo("4850");
            assertThat(s.ciudad()).isEqualTo("Ciudad Autónoma de Buenos Aires");
            assertThat(s.provincia()).isEqualTo("Ciudad Autónoma de Buenos Aires");
            assertThat(s.latitud()).isEqualByComparingTo(new BigDecimal("-34.5903465757709"));
            assertThat(s.placeId()).isEqualTo("georef:0209801005940:4850");
            assertThat(s.descripcion()).startsWith("AV. DEL LIBERTADOR 4850");
        });
        assertThat(server.queries()).singleElement().satisfies(query ->
                assertThat(query).contains("direccion=Libertador%204850").contains("max=3"));
        assertThat(server.requestIds()).containsExactly("req-georef-1");
    }

    @Test
    void badRequestMeansNoResults() {
        server.respond(400, "{\"errores\":[]}");

        assertThat(provider.buscar("???", 5)).isEmpty();
    }

    @Test
    void serverErrorsAndThrottlingAreTransient() {
        server.respond(503, "{}");
        assertThatThrownBy(() -> provider.buscar("Gorriti", 5))
                .isInstanceOf(AddressProviderTransientException.class).matches(transientFailure);

        server.respond(429, "{}");
        assertThatThrownBy(() -> provider.buscar("Gorriti", 5))
                .isInstanceOf(AddressProviderTransientException.class);
    }

    @Test
    void rejectedCredentialsAndInvalidResponsesArePermanent() {
        server.respond(401, "{}");
        assertThatThrownBy(() -> provider.buscar("Gorriti", 5))
                .isInstanceOf(AddressProviderException.class).matches(transientFailure.negate());

        server.respond(200, "<html>no es json</html>");
        assertThatThrownBy(() -> provider.buscar("Gorriti", 5))
                .isInstanceOf(AddressProviderException.class).matches(transientFailure.negate());
    }

    @Test
    void slowResponsesTimeOutAsTransientFailures() {
        server.respondSlowly(TIMEOUT.multipliedBy(3).toMillis());

        assertThatThrownBy(() -> provider.buscar("Gorriti", 5))
                .isInstanceOf(AddressProviderTransientException.class);
    }

    @Test
    void streetNamesAreTitleCased() {
        server.respond(200, FakeGeorefServer.RESPUESTA_OK.replace("AV. DEL LIBERTADOR", "GRAL. JUAN D. PERÓN"));

        assertThat(provider.buscar("Perón 4850", 1).getFirst().calle()).isEqualTo("Gral. Juan D. Perón");
    }
}
