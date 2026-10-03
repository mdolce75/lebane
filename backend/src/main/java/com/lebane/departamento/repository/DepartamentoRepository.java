package com.lebane.departamento.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.lebane.departamento.entity.Departamento;

/**
 * Repositorio de departamentos.
 *
 * <ul>
 *   <li>{@link JpaSpecificationExecutor}: {@code count(spec)} del listado, con los mismos predicados que la página y
 *       sin JOINs, y {@code exists(spec)} de las reglas de negocio.</li>
 *   <li>{@link DepartamentoListadoRepository}: página proyectada a DTO y agregados por página.</li>
 *   <li>{@link DepartamentoClaveRepository}: id por código y bloqueo de la fila.</li>
 * </ul>
 * Sin consultas escritas como texto ni derivadas del nombre del método: todo es Criteria API con el metamodelo
 * estático. No se usa {@code findAll()} para el listado: nunca se cargan todas las entidades en memoria.
 */
public interface DepartamentoRepository extends JpaRepository<Departamento, Long>,
        JpaSpecificationExecutor<Departamento>, DepartamentoListadoRepository, DepartamentoClaveRepository {

    /** Usa el índice único de {@code codigo}. */
    default boolean existsByCodigo(String codigo) {
        return exists(DepartamentoSpecifications.conCodigo(codigo));
    }
}
