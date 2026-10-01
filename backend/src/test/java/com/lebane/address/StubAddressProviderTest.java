package com.lebane.address;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.lebane.address.provider.StubAddressProvider;

class StubAddressProviderTest {

    private final StubAddressProvider provider = new StubAddressProvider();

    @Test
    void matchesIgnoringAccentsAndCaseAndKeepsTheTypedNumber() {
        assertThat(provider.buscar("GORRITI 4850", 5))
                .extracting("calle", "numero", "ciudad")
                .contains(org.assertj.core.groups.Tuple.tuple("Gorriti", "4850", "Ciudad Autónoma de Buenos Aires"));
        assertThat(provider.buscar("av colon", 5)).extracting("calle").containsOnly("Av. Colón");
    }

    @Test
    void neverInventsCoordinates() {
        assertThat(provider.buscar("Corrientes 1234", 5)).allSatisfy(s -> {
            assertThat(s.latitud()).isNull();
            assertThat(s.longitud()).isNull();
            assertThat(s.placeId()).startsWith("stub:");
        });
    }

    @Test
    void respectsTheLimitAndHandlesNoMatches() {
        assertThat(provider.buscar("av", 2)).hasSize(2);
        assertThat(provider.buscar("calle inexistente", 5)).isEmpty();
        assertThat(provider.buscar("1234", 5)).isEmpty();
    }
}
