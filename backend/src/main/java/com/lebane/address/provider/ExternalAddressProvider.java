package com.lebane.address.provider;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.lebane.address.client.GeorefClient;
import com.lebane.address.client.GeorefResponse;
import com.lebane.address.dto.SugerenciaDireccion;

/**
 * Proveedor externo: API Georef del Estado argentino (normalización de direcciones; gratuita, sin API key).
 * Traduce su modelo al de la aplicación; ningún tipo de Georef sale de este paquete y del cliente. Para usar otro
 * proveedor basta con otra implementación de {@link AddressProvider}.
 */
public class ExternalAddressProvider implements AddressProvider {

    private final GeorefClient client;

    public ExternalAddressProvider(GeorefClient client) {
        this.client = client;
    }

    @Override
    public String nombre() {
        return "georef";
    }

    @Override
    public List<SugerenciaDireccion> buscar(String texto, int limite) {
        return client.buscarDirecciones(texto, limite).stream()
                .filter(d -> d.calle() != null && d.calle().nombre() != null)
                .map(ExternalAddressProvider::toSugerencia)
                .limit(limite)
                .toList();
    }

    private static SugerenciaDireccion toSugerencia(GeorefResponse.Direccion d) {
        String numero = d.altura() != null && d.altura().valor() != null ? d.altura().valor().toString() : null;
        String ciudad = d.localidadCensal() != null ? d.localidadCensal().nombre() : null;
        String provincia = d.provincia() != null ? d.provincia().nombre() : null;
        String placeId = "georef:" + Objects.requireNonNullElse(d.calle().id(), "?") + ":"
                + Objects.requireNonNullElse(numero, "");
        return new SugerenciaDireccion(
                tituloCalle(d.calle().nombre()),
                numero,
                ciudad,
                provincia,
                d.ubicacion() != null ? d.ubicacion().lat() : null,
                d.ubicacion() != null ? d.ubicacion().lon() : null,
                placeId,
                d.nomenclatura());
    }

    /** Georef devuelve las calles en mayúsculas ("AV. DEL LIBERTADOR"); se presentan como "Av. Del Libertador". */
    static String tituloCalle(String nombre) {
        StringBuilder result = new StringBuilder(nombre.length());
        boolean inicioPalabra = true;
        for (char c : nombre.toLowerCase(Locale.of("es", "AR")).toCharArray()) {
            result.append(inicioPalabra ? Character.toUpperCase(c) : c);
            inicioPalabra = Character.isWhitespace(c) || c == '.' || c == '-';
        }
        return result.toString();
    }
}
