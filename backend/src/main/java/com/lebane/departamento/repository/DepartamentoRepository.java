package com.lebane.departamento.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lebane.departamento.entity.Departamento;

public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    /** Usa el índice único de {@code codigo}. */
    boolean existsByCodigo(String codigo);
}
