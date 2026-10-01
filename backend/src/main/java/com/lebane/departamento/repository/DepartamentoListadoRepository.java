package com.lebane.departamento.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.lebane.departamento.entity.Departamento;

/**
 * Consultas del listado que Spring Data no puede derivar: proyección a DTO con filtros dinámicos y agregados por
 * página. Fragmento de {@link DepartamentoRepository}.
 */
public interface DepartamentoListadoRepository {

    /**
     * Una página del listado: filtros, orden, OFFSET y LIMIT en PostgreSQL, proyectando solo las columnas necesarias.
     */
    List<DepartamentoListadoRow> buscarPagina(Specification<Departamento> spec, Pageable pageable);

    /**
     * Imagen principal, cantidad de imágenes y cantidad de consultas de los departamentos indicados (una página),
     * calculados en una única consulta. Los IDs sin fotos ni consultas devuelven ceros.
     */
    Map<Long, DepartamentoAgregados> agregados(Collection<Long> departamentoIds);
}
