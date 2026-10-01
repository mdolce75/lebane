package com.lebane.departamento.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class CampoOrdenTest {

    @Test
    void defaultIsNewestFirstWithIdTieBreaker() {
        assertThat(CampoOrden.toSort(CampoOrden.DEFAULT))
                .isEqualTo(Sort.by(DESC, "createdAt", "id"));
        assertThat(CampoOrden.toSort("createdAt")).isEqualTo(Sort.by(DESC, "createdAt", "id"));
    }

    @Test
    void precioSortsWithinCurrencyInASingleDirection() {
        assertThat(CampoOrden.toSort("precio")).isEqualTo(Sort.by(ASC, "moneda", "precio", "id"));
        assertThat(CampoOrden.toSort("precio,DESC")).isEqualTo(Sort.by(DESC, "moneda", "precio", "id"));
    }

    @Test
    void superficieSort() {
        assertThat(CampoOrden.toSort("superficieM2,desc")).isEqualTo(Sort.by(DESC, "superficieM2", "id"));
    }

    @Test
    void patternOnlyAcceptsWhitelistedFields() {
        assertThat("precio,asc").matches(CampoOrden.PATTERN);
        assertThat("titulo").doesNotMatch(CampoOrden.PATTERN);
        assertThat("precio;drop table").doesNotMatch(CampoOrden.PATTERN);
        assertThat("precio,sideways").doesNotMatch(CampoOrden.PATTERN);
    }
}
