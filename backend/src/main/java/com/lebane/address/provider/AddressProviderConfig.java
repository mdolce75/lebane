package com.lebane.address.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.lebane.address.client.GeorefClient;

/** Selecciona la implementación de {@link AddressProvider} según {@code ADDRESS_PROVIDER}. */
@Configuration(proxyBeanMethods = false)
public class AddressProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "lebane.address", name = "provider", havingValue = "external")
    AddressProvider externalAddressProvider(RestClient.Builder restClientBuilder, AddressProperties properties) {
        if (properties.url() == null || properties.url().isBlank()) {
            throw new IllegalStateException("ADDRESS_PROVIDER=external requiere ADDRESS_PROVIDER_URL");
        }
        return new ExternalAddressProvider(new GeorefClient(restClientBuilder, properties));
    }

    @Bean
    @ConditionalOnProperty(prefix = "lebane.address", name = "provider", havingValue = "stub", matchIfMissing = true)
    AddressProvider stubAddressProvider() {
        return new StubAddressProvider();
    }
}
