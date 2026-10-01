package com.lebane.departamento.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.lebane.departamento.entity.Departamento;

/**
 * Repositorio de departamentos.
 *
 * <ul>
 *   <li>{@link JpaSpecificationExecutor}: {@code count(spec)} del listado, con los mismos predicados que la página y
 *       sin JOINs.</li>
 *   <li>{@link DepartamentoListadoRepository}: página proyectada a DTO y agregados por página.</li>
 * </ul>
 * No se usa {@code findAll()} para el listado: nunca se cargan todas las entidades en memoria.
 */
public interface DepartamentoRepository extends JpaRepository<Departamento, Long>,
        JpaSpecificationExecutor<Departamento>, DepartamentoListadoRepository {

    /** Usa el índice único de {@code codigo}. */
    boolean existsByCodigo(String codigo);
}
