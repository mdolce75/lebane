package com.lebane.departamento.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lebane.departamento.dto.CampoOrden;
import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.DepartamentoAgregados;
import com.lebane.departamento.repository.DepartamentoListadoRow;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.DepartamentoSpecifications;

/**
 * Listado paginado de departamentos. Todo se resuelve en PostgreSQL con una cantidad fija de consultas por página,
 * independiente del tamaño de página y de la cantidad de fotos o consultas:
 * <ol>
 *   <li>página proyectada a DTO (filtros + orden + OFFSET/LIMIT);</li>
 *   <li>{@code COUNT} con los mismos filtros, omitido cuando el total se deduce de la propia página (p. ej. la
 *       primera página con menos filas que el tamaño pedido);</li>
 *   <li>agregados (imagen principal, cantidad de fotos y de consultas) solo para los IDs de la página.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class DepartamentoListadoService {

    private final DepartamentoRepository repository;
    private final DepartamentoMapper mapper;

    public DepartamentoListadoService(DepartamentoRepository repository, DepartamentoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public PagedModel<DepartamentoListItemResponse> listar(DepartamentoListadoParams params) {
        Specification<Departamento> spec = DepartamentoSpecifications.conFiltro(mapper.toFiltro(params));
        Pageable pageable = PageRequest.of(params.page(), params.size(), CampoOrden.toSort(params.sort()));

        List<DepartamentoListadoRow> rows = repository.buscarPagina(spec, pageable);
        Map<Long, DepartamentoAgregados> agregados = rows.isEmpty()
                ? Map.of()
                : repository.agregados(rows.stream().map(DepartamentoListadoRow::id).toList());
        // Conserva el orden de la página; los agregados se indexan por ID (búsqueda O(1), sin consultas extra).
        List<DepartamentoListItemResponse> items = rows.stream()
                .map(row -> mapper.toListItem(row, agregados.get(row.id())))
                .toList();

        Page<DepartamentoListItemResponse> page =
                PageableExecutionUtils.getPage(items, pageable, () -> repository.count(spec));
        return new PagedModel<>(page);
    }
}
