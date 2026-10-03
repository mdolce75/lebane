package com.lebane.departamento.entity;

import static com.lebane.departamento.entity.EstadoDepartamento.DISPONIBLE;
import static com.lebane.departamento.entity.EstadoDepartamento.RESERVADO;
import static com.lebane.departamento.entity.EstadoDepartamento.VENDIDO;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Ciclo de vida: DISPONIBLE ⇄ RESERVADO, ambos → VENDIDO, y VENDIDO es final. */
class EstadoDepartamentoTest {

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
            "DISPONIBLE, DISPONIBLE, true",
            "DISPONIBLE, RESERVADO,  true",
            "DISPONIBLE, VENDIDO,    true",
            "RESERVADO,  DISPONIBLE, true",
            "RESERVADO,  RESERVADO,  true",
            "RESERVADO,  VENDIDO,    true",
            "VENDIDO,    DISPONIBLE, false",
            "VENDIDO,    RESERVADO,  false",
            "VENDIDO,    VENDIDO,    true"
    })
    void matrizDeTransiciones(EstadoDepartamento desde, EstadoDepartamento hasta, boolean permitida) {
        assertThat(desde.puedeCambiarA(hasta)).isEqualTo(permitida);
    }

    @Test
    void vendidoEsUnRegistroCerrado() {
        assertThat(VENDIDO.transicionesPermitidas()).isEmpty();
        assertThat(VENDIDO.esModificable()).isFalse();
        assertThat(VENDIDO.aceptaConsultas()).isFalse();
        assertThat(VENDIDO.admiteAlta()).isFalse();
    }

    @Test
    void disponibleYReservadoSonModificablesYAdmitenAlta() {
        for (EstadoDepartamento estado : new EstadoDepartamento[] {DISPONIBLE, RESERVADO}) {
            assertThat(estado.esModificable()).as(estado.name()).isTrue();
            assertThat(estado.admiteAlta()).as(estado.name()).isTrue();
            assertThat(estado.aceptaConsultas()).as(estado.name()).isTrue();
        }
    }
}
