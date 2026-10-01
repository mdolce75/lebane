package com.lebane.departamento.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lebane.departamento.entity.Departamento;

import jakarta.persistence.LockModeType;

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

    @Query("select d.id from Departamento d where d.codigo = :codigo")
    Optional<Long> findIdByCodigo(@Param("codigo") String codigo);

    /**
     * Bloquea la fila del departamento ({@code SELECT ... FOR UPDATE}) hasta el fin de la transacción. Serializa las
     * altas de fotos de un mismo departamento para que el límite de 5 se respete con subidas concurrentes, sin
     * bloquear lecturas ni otros departamentos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d.id from Departamento d where d.id = :id")
    Optional<Long> lockById(@Param("id") Long id);
}
