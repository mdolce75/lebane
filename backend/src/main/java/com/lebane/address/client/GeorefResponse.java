package com.lebane.address.client;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subconjunto de la respuesta de {@code GET /direcciones} de la API Georef (datos.gob.ar). Solo se mapea lo que se
 * usa; los campos desconocidos se ignoran para tolerar cambios compatibles del proveedor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeorefResponse(List<Direccion> direcciones) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Direccion(
            Altura altura,
            Entidad calle,
            @JsonProperty("localidad_censal") Entidad localidadCensal,
            Entidad provincia,
            Ubicacion ubicacion,
            String nomenclatura) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Altura(Object valor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entidad(String id, String nombre) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ubicacion(BigDecimal lat, BigDecimal lon) {
    }
}
