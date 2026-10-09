package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;

import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.DepartamentoAgregados;
import com.lebane.departamento.repository.DepartamentoListadoRow;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.storage.TestStorageProperties;
import com.lebane.storage.service.PublicBucketImageUrlResolver;

@ExtendWith(MockitoExtension.class)
class DepartamentoListadoServiceTest {

    @Mock
    private DepartamentoRepository repository;

    private DepartamentoListadoService service;

    @BeforeEach
    void setUp() {
        service = new DepartamentoListadoService(repository, new DepartamentoMapper(
                new PublicBucketImageUrlResolver(TestStorageProperties.of("http://cdn", "b"))));
    }

    @Test
    void combinesPageRowsWithTheirAggregatesKeepingOrder() {
        when(repository.buscarPagina(any(), any())).thenReturn(List.of(row(3L), row(1L), row(2L)));
        when(repository.agregados(List.of(3L, 1L, 2L))).thenReturn(Map.of(
                3L, new DepartamentoAgregados(2, "departamentos/3/a.jpg", 5),
                1L, new DepartamentoAgregados(0, null, 1)));

        PagedModel<DepartamentoListItemResponse> result = service.listar(params(0, 20));

        assertThat(result.getContent()).extracting(DepartamentoListItemResponse::id).containsExactly(3L, 1L, 2L);
        DepartamentoListItemResponse first = result.getContent().getFirst();
        assertThat(first.imagenPrincipalUrl()).isEqualTo("http://cdn/b/departamentos/3/a.jpg");
        assertThat(first.cantidadImagenes()).isEqualTo(2);
        assertThat(first.cantidadConsultas()).isEqualTo(5);
        // Sin fila de agregados (no debería ocurrir): ceros y sin imagen, nunca null.
        DepartamentoListItemResponse last = result.getContent().getLast();
        assertThat(last.imagenPrincipalUrl()).isNull();
        assertThat(last.cantidadImagenes()).isZero();
    }

    @Test
    void partialFirstPageSkipsTheCountQuery() {
        when(repository.buscarPagina(any(), any())).thenReturn(List.of(row(1L), row(2L)));
        when(repository.agregados(anyCollection())).thenReturn(Map.of());

        PagedModel<DepartamentoListItemResponse> result = service.listar(params(0, 20));

        assertThat(result.getMetadata().totalElements()).isEqualTo(2);
        verify(repository, never()).count(any(Specification.class));
    }

    @Test
    void fullPageRunsTheCountQuery() {
        List<DepartamentoListadoRow> fullPage = LongStream.rangeClosed(1, 2).mapToObj(this::row).toList();
        when(repository.buscarPagina(any(), any())).thenReturn(fullPage);
        when(repository.agregados(anyCollection())).thenReturn(Map.of());
        when(repository.count(any(Specification.class))).thenReturn(57L);

        PagedModel<DepartamentoListItemResponse> result = service.listar(params(0, 2));

        assertThat(result.getMetadata().totalElements()).isEqualTo(57);
        assertThat(result.getMetadata().totalPages()).isEqualTo(29);
    }

    @Test
    void emptyPageSkipsTheAggregatesQuery() {
        when(repository.buscarPagina(any(), any())).thenReturn(List.of());
        when(repository.count(any(Specification.class))).thenReturn(3L);

        PagedModel<DepartamentoListItemResponse> result = service.listar(params(5, 20));

        assertThat(result.getContent()).isEmpty();
        verify(repository, never()).agregados(anyCollection());
    }

    @Test
    void translatesPagingAndSortingToTheRepository() {
        when(repository.buscarPagina(any(), any())).thenReturn(List.of());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        service.listar(new DepartamentoListadoParams(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, 2, 10, "precio,desc"));

        verify(repository).buscarPagina(any(), pageable.capture());
        assertThat(pageable.getValue().getOffset()).isEqualTo(20);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "moneda", "precio", "id"));
    }

    private static DepartamentoListadoParams params(int page, int size) {
        return new DepartamentoListadoParams(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                page, size, null);
    }

    private DepartamentoListadoRow row(long id) {
        return new DepartamentoListadoRow(id, "DEP-" + id, "Título " + id, new BigDecimal("100000"), Moneda.USD, 3,
                2, 1, new BigDecimal("70"), EstadoDepartamento.DISPONIBLE, "CABA", "CABA",
                Instant.parse("2026-10-01T12:00:00Z"), null);
    }
}
