package com.lebane.departamento.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.mapper.DepartamentoMapper;

/** Criterio de "misma dirección" de la regla de avisos duplicados. */
class DireccionTest {

    private static final Direccion BASE = direccion("Gorriti", "4850", "7", "B", "C1414", "-34.588900");

    @Test
    void laMismaUnidadSinDistinguirMayusculasNiDatosAccesorios() {
        assertThat(BASE.mismaUbicacion(direccion("GORRITI", "4850", "7", "b", null, null))).isTrue();
        assertThat(BASE.mismaUbicacion(direccion("Gorriti", "4850", "7", "B", "C1414", "-34.600000"))).isTrue();
    }

    @Test
    void otroPisoOtraUnidadUOtraAlturaSonOtraUbicacion() {
        assertThat(BASE.mismaUbicacion(direccion("Gorriti", "4850", "8", "B", "C1414", "-34.588900"))).isFalse();
        assertThat(BASE.mismaUbicacion(direccion("Gorriti", "4850", "7", "C", "C1414", "-34.588900"))).isFalse();
        assertThat(BASE.mismaUbicacion(direccion("Gorriti", "4851", "7", "B", "C1414", "-34.588900"))).isFalse();
        assertThat(BASE.mismaUbicacion(direccion("Gorriti", "4850", "7", null, "C1414", "-34.588900"))).isFalse();
        assertThat(BASE.mismaUbicacion(null)).isFalse();
    }

    @Test
    void sinPisoNiUnidadCoincideConOtraSinPisoNiUnidad() {
        Direccion casa = direccion("Gorriti", "4850", null, null, null, null);
        assertThat(casa.mismaUbicacion(direccion("Gorriti", "4850", " ", "", null, null))).isTrue();
    }

    private static Direccion direccion(String calle, String numero, String piso, String unidad, String codigoPostal,
            String latitud) {
        BigDecimal lat = latitud == null ? null : new BigDecimal(latitud);
        BigDecimal lon = latitud == null ? null : new BigDecimal("-58.430500");
        return DepartamentoMapper.toDireccion(new DireccionRequest(calle, numero, piso, unidad,
                "Ciudad Autónoma de Buenos Aires", "CABA", codigoPostal, lat, lon, null));
    }
}
