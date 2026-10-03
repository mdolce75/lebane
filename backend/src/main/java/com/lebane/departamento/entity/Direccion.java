package com.lebane.departamento.entity;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Dirección del departamento. Se modela como {@link Embeddable} (columnas en la tabla {@code departamento}): la
 * relación es 1:1 y siempre se lee junto con el departamento, por lo que una tabla aparte solo agregaría un JOIN a
 * cada consulta (incluido el listado filtrado por ciudad) sin ningún beneficio.
 */
@Embeddable
public class Direccion {

    @Column(name = "calle", nullable = false, length = 120)
    private String calle;

    @Column(name = "numero", nullable = false, length = 10)
    private String numero;

    @Column(name = "piso", length = 10)
    private String piso;

    @Column(name = "unidad", length = 10)
    private String unidad;

    @Column(name = "ciudad", nullable = false, length = 80)
    private String ciudad;

    @Column(name = "provincia", nullable = false, length = 80)
    private String provincia;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "latitud", precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(name = "longitud", precision = 9, scale = 6)
    private BigDecimal longitud;

    /** Identificador del proveedor de autocomplete del que se obtuvo la dirección (opcional). */
    @Column(name = "place_id", length = 200)
    private String placeId;

    protected Direccion() {
        // JPA
    }

    public Direccion(String calle, String numero, String piso, String unidad, String ciudad, String provincia,
            String codigoPostal, BigDecimal latitud, BigDecimal longitud, String placeId) {
        this.calle = calle;
        this.numero = numero;
        this.piso = piso;
        this.unidad = unidad;
        this.ciudad = ciudad;
        this.provincia = provincia;
        this.codigoPostal = codigoPostal;
        this.latitud = latitud;
        this.longitud = longitud;
        this.placeId = placeId;
    }

    public String getCalle() {
        return calle;
    }

    public String getNumero() {
        return numero;
    }

    public String getPiso() {
        return piso;
    }

    public String getUnidad() {
        return unidad;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getProvincia() {
        return provincia;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public String getPlaceId() {
        return placeId;
    }

    /**
     * Misma unidad física: calle, número, piso, unidad, ciudad y provincia, sin distinguir mayúsculas. No compara
     * código postal, coordenadas ni {@code placeId}: la misma dirección puede cargarse con o sin ellos.
     */
    public boolean mismaUbicacion(Direccion otra) {
        return otra != null && igualSinMayusculas(calle, otra.calle) && igualSinMayusculas(numero, otra.numero)
                && igualSinMayusculas(piso, otra.piso) && igualSinMayusculas(unidad, otra.unidad)
                && igualSinMayusculas(ciudad, otra.ciudad) && igualSinMayusculas(provincia, otra.provincia);
    }

    private static boolean igualSinMayusculas(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Direccion other)) {
            return false;
        }
        return Objects.equals(calle, other.calle) && Objects.equals(numero, other.numero)
                && Objects.equals(piso, other.piso) && Objects.equals(unidad, other.unidad)
                && Objects.equals(ciudad, other.ciudad) && Objects.equals(provincia, other.provincia)
                && Objects.equals(codigoPostal, other.codigoPostal) && Objects.equals(latitud, other.latitud)
                && Objects.equals(longitud, other.longitud) && Objects.equals(placeId, other.placeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(calle, numero, piso, unidad, ciudad, provincia, codigoPostal, latitud, longitud, placeId);
    }
}
