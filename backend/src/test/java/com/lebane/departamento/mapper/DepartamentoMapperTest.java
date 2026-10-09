package com.lebane.departamento.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Moneda;
import com.lebane.departamento.repository.DepartamentoAgregados;
import com.lebane.departamento.repository.DepartamentoListadoRow;
import com.lebane.storage.TestStorageProperties;
import com.lebane.storage.service.PublicBucketImageUrlResolver;

class DepartamentoMapperTest {

    private final DepartamentoMapper mapper = new DepartamentoMapper(
            new PublicBucketImageUrlResolver(TestStorageProperties.of("http://localhost:9000/", "lebane-images")));

    @Test
    void newEntityDefaultsToDisponibleAndNormalizesText() {
        DepartamentoRequest request = new DepartamentoRequest("  Título con espacios  ", "   ",
                new BigDecimal("100"), Moneda.ARS, 2, 1, 1, new BigDecimal("40"), null,
                new DireccionRequest(" Gorriti ", " 4850 ", " ", "", " CABA ", " CABA ", "  ", null, null, " "));

        Departamento entity = mapper.toNewEntity(request, "DEP-TEST0001");

        assertThat(entity.getCodigo()).isEqualTo("DEP-TEST0001");
        assertThat(entity.getEstado()).isEqualTo(EstadoDepartamento.DISPONIBLE);
        assertThat(entity.getTitulo()).isEqualTo("Título con espacios");
        assertThat(entity.getDescripcion()).isNull();
        assertThat(entity.getDireccion().getCalle()).isEqualTo("Gorriti");
        assertThat(entity.getDireccion().getPiso()).isNull();
        assertThat(entity.getDireccion().getUnidad()).isNull();
        assertThat(entity.getDireccion().getCodigoPostal()).isNull();
        assertThat(entity.getDireccion().getPlaceId()).isNull();
    }

    @Test
    void newEntityKeepsExplicitEstado() {
        Departamento entity = mapper.toNewEntity(TestFixtures.departamento(3, 2, EstadoDepartamento.RESERVADO), "C");

        assertThat(entity.getEstado()).isEqualTo(EstadoDepartamento.RESERVADO);
    }

    @Test
    void updateWithoutEstadoKeepsCurrentEstado() {
        Departamento entity = mapper.toNewEntity(TestFixtures.departamento(3, 2, EstadoDepartamento.RESERVADO), "C");

        mapper.applyUpdate(entity, TestFixtures.departamento(4, 3, null));

        assertThat(entity.getEstado()).isEqualTo(EstadoDepartamento.RESERVADO);
        assertThat(entity.getAmbientes()).isEqualTo(4);
        assertThat(entity.getDormitorios()).isEqualTo(3);
    }

    @Test
    void updateWithEstadoChangesIt() {
        Departamento entity = mapper.toNewEntity(TestFixtures.departamento(), "C");

        mapper.applyUpdate(entity, TestFixtures.departamento(3, 2, EstadoDepartamento.VENDIDO));

        assertThat(entity.getEstado()).isEqualTo(EstadoDepartamento.VENDIDO);
    }

    @Test
    void detailIncludesImagesWithPublicUrlsAndCounters() {
        Departamento entity = mapper.toNewEntity(TestFixtures.departamento(), "DEP-TEST0001");
        ReflectionTestUtils.setField(entity, "id", 42L);
        ReflectionTestUtils.setField(entity, "version", 3L);
        Imagen principal = new Imagen(entity, "departamentos/42/a.jpg", "image/jpeg", 1024, 0);
        Imagen segunda = new Imagen(entity, "departamentos/42/b.png", "image/png", 2048, 1);
        ReflectionTestUtils.setField(principal, "id", 1L);
        ReflectionTestUtils.setField(segunda, "id", 2L);

        List<com.lebane.departamento.dto.ConsultaResponse> consultas = List.of(
                new com.lebane.departamento.dto.ConsultaResponse(5L, "Ana", "ana@example.com", null, "Hola, ¿sigue?",
                        java.time.Instant.parse("2026-10-01T12:00:00Z")),
                new com.lebane.departamento.dto.ConsultaResponse(4L, "Beto", "beto@example.com", null, "¿Expensas?",
                        java.time.Instant.parse("2026-09-30T12:00:00Z")));
        DepartamentoDetailResponse detail = mapper.toDetail(entity, List.of(principal, segunda), consultas);

        assertThat(detail.id()).isEqualTo(42L);
        assertThat(detail.codigo()).isEqualTo("DEP-TEST0001");
        assertThat(detail.version()).isEqualTo(3L);
        assertThat(detail.cantidadConsultas()).isEqualTo(2);
        assertThat(detail.consultas()).extracting("id").containsExactly(5L, 4L);
        assertThat(detail.direccion().ciudad()).isEqualTo("Ciudad Autónoma de Buenos Aires");
        assertThat(detail.imagenes()).extracting("id", "url", "posicion").containsExactly(
                org.assertj.core.groups.Tuple.tuple(1L, "http://localhost:9000/lebane-images/departamentos/42/a.jpg", 0),
                org.assertj.core.groups.Tuple.tuple(2L, "http://localhost:9000/lebane-images/departamentos/42/b.png", 1));
    }

    @Test
    void listItemResolvesMainImageUrlOrNull() {
        DepartamentoListadoRow row = new DepartamentoListadoRow(7L, "DEP-X", "Título", new BigDecimal("1"),
                Moneda.USD, 2, 1, 1, new BigDecimal("40"), EstadoDepartamento.DISPONIBLE, "CABA", "CABA",
                Instant.parse("2026-10-01T12:00:00Z"), null);

        var conFoto = mapper.toListItem(row, new DepartamentoAgregados(3, "departamentos/7/p.jpg", 9));
        var sinFoto = mapper.toListItem(row, null);

        assertThat(conFoto.imagenPrincipalUrl())
                .isEqualTo("http://localhost:9000/lebane-images/departamentos/7/p.jpg");
        assertThat(conFoto.cantidadImagenes()).isEqualTo(3);
        assertThat(conFoto.cantidadConsultas()).isEqualTo(9);
        assertThat(sinFoto.imagenPrincipalUrl()).isNull();
        assertThat(sinFoto.cantidadImagenes()).isZero();
        assertThat(sinFoto.cantidadConsultas()).isZero();
    }

    @Test
    void filtroCollapsesRepeatedStates() {
        var params = new DepartamentoListadoParams(null, null,
                List.of(EstadoDepartamento.VENDIDO, EstadoDepartamento.VENDIDO), null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        var sinEstados = new DepartamentoListadoParams(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThat(mapper.toFiltro(params).estados()).containsExactly(EstadoDepartamento.VENDIDO);
        assertThat(mapper.toFiltro(sinEstados).estados()).isEmpty();
    }
}
