package com.lebane.departamento.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Baja lógica: guarda la fecha, no se repite y se revierte con la reactivación. */
class DepartamentoBajaTest {

    private static final Instant FECHA = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void unDepartamentoNuevoEstaVigente() {
        Departamento departamento = new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE);

        assertThat(departamento.estaDadoDeBaja()).isFalse();
        assertThat(departamento.getFechaBaja()).isNull();
    }

    @Test
    void laBajaGuardaLaFechaYNoCambiaElEstado() {
        Departamento departamento = new Departamento("DEP-X", EstadoDepartamento.RESERVADO);

        departamento.darDeBaja(FECHA);

        assertThat(departamento.estaDadoDeBaja()).isTrue();
        assertThat(departamento.getFechaBaja()).isEqualTo(FECHA);
        assertThat(departamento.getEstado()).isEqualTo(EstadoDepartamento.RESERVADO);
    }

    @Test
    void laReactivacionLoVuelveAPublicarConElMismoEstado() {
        Departamento departamento = new Departamento("DEP-X", EstadoDepartamento.RESERVADO);
        departamento.darDeBaja(FECHA);

        departamento.reactivar();

        assertThat(departamento.estaDadoDeBaja()).isFalse();
        assertThat(departamento.getFechaBaja()).isNull();
        assertThat(departamento.getEstado()).isEqualTo(EstadoDepartamento.RESERVADO);
    }

    @Test
    void soloSeReactivaUnoDadoDeBaja() {
        Departamento departamento = new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE);

        assertThatThrownBy(departamento::reactivar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noSeDaDeBajaDosVeces() {
        Departamento departamento = new Departamento("DEP-X", EstadoDepartamento.VENDIDO);
        departamento.darDeBaja(FECHA);

        assertThatThrownBy(() -> departamento.darDeBaja(FECHA.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(departamento.getFechaBaja()).isEqualTo(FECHA);
    }
}
