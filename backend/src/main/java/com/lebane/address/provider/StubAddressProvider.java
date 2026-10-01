package com.lebane.address.provider;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lebane.address.dto.SugerenciaDireccion;

/**
 * Proveedor local para desarrollo y tests: busca en un catálogo fijo de calles, sin red. Sin acentos ni mayúsculas
 * ({@code "gorriti 48"} encuentra "Gorriti"). La altura es la que tipeó el usuario; no informa coordenadas porque no
 * las conoce (nunca se inventan). La respuesta indica {@code proveedor: "stub"}.
 */
public class StubAddressProvider implements AddressProvider {

    private static final Pattern NUMERO = Pattern.compile("\\d{1,5}");

    private record Calle(String calle, String ciudad, String provincia) {
    }

    private static final List<Calle> CATALOGO = List.of(
            new Calle("Gorriti", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Av. Santa Fe", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Av. Corrientes", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Av. Cabildo", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Av. del Libertador", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Av. Rivadavia", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Defensa", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Hidalgo", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Av. Triunvirato", "Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires"),
            new Calle("Gorriti", "Santa Fe", "Santa Fe"),
            new Calle("Bv. Oroño", "Rosario", "Santa Fe"),
            new Calle("Av. Pellegrini", "Rosario", "Santa Fe"),
            new Calle("Bv. Chacabuco", "Córdoba", "Córdoba"),
            new Calle("Av. Colón", "Córdoba", "Córdoba"),
            new Calle("Av. San Martín", "Mendoza", "Mendoza"),
            new Calle("Av. Colón", "Mar del Plata", "Buenos Aires"),
            new Calle("Calle 7", "La Plata", "Buenos Aires"),
            new Calle("Av. Mate de Luna", "San Miguel de Tucumán", "Tucumán"));

    @Override
    public String nombre() {
        return "stub";
    }

    @Override
    public List<SugerenciaDireccion> buscar(String texto, int limite) {
        Matcher matcher = NUMERO.matcher(texto);
        String numero = matcher.find() ? matcher.group() : null;
        String calleBuscada = normalizar(NUMERO.matcher(texto).replaceAll(" "));
        if (calleBuscada.isBlank()) {
            return List.of();
        }
        return CATALOGO.stream()
                .filter(c -> normalizar(c.calle()).contains(calleBuscada)
                        || normalizar(c.calle() + " " + c.ciudad()).contains(calleBuscada))
                .limit(limite)
                .map(c -> new SugerenciaDireccion(c.calle(), numero, c.ciudad(), c.provincia(), null, null,
                        "stub:" + CATALOGO.indexOf(c),
                        c.calle() + (numero != null ? " " + numero : "") + ", " + c.ciudad() + ", " + c.provincia()))
                .toList();
    }

    static String normalizar(String value) {
        String sinAcentos = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }
}
