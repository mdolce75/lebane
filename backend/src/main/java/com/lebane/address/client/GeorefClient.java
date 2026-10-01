package com.lebane.address.client;

import java.net.http.HttpClient;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.lebane.address.provider.AddressProperties;
import com.lebane.logging.interceptor.RequestIdPropagationInterceptor;

/**
 * Cliente HTTP de la API Georef ({@code GET /direcciones}). Construido sobre el {@code RestClient.Builder} de Spring
 * Boot: hereda la instrumentación de Micrometer (métrica {@code http.client.requests} y propagación de
 * {@code traceparent}) y agrega el {@code X-Request-Id} del request actual.
 *
 * <p>Clasificación de errores: red, 5xx y 429 son transitorios; 400 (dirección que el proveedor no puede
 * interpretar) es "sin resultados"; el resto de 4xx (credenciales, URL mal configurada) es permanente.
 */
public class GeorefClient {

    private final RestClient restClient;

    public GeorefClient(RestClient.Builder builder, AddressProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        RestClient.Builder configured = builder.clone()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .requestInterceptor(new RequestIdPropagationInterceptor())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.hasApiKey()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        this.restClient = configured.build();
    }

    public List<GeorefResponse.Direccion> buscarDirecciones(String texto, int max) {
        try {
            GeorefResponse response = restClient.get()
                    .uri(uri -> uri.path("/direcciones").queryParam("direccion", texto).queryParam("max", max)
                            .build())
                    .retrieve()
                    .body(GeorefResponse.class);
            return response == null || response.direcciones() == null ? List.of() : response.direcciones();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                return List.of();
            }
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new AddressProviderTransientException("Address provider throttled the request", e);
            }
            throw new AddressProviderException("Address provider rejected the request: HTTP "
                    + e.getStatusCode().value(), e);
        } catch (HttpServerErrorException e) {
            throw new AddressProviderTransientException("Address provider error: HTTP " + e.getStatusCode().value(),
                    e);
        } catch (ResourceAccessException e) {
            throw new AddressProviderTransientException("Address provider unreachable", e);
        } catch (RestClientException e) {
            throw new AddressProviderException("Invalid response from address provider", e);
        }
    }
}
