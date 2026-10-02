package com.lebane.departamento.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lebane.departamento.entity.Consulta;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    /** COUNT en la base usando {@code ix_consulta_departamento}; nunca se cargan las consultas en memoria. */
    @Query("select count(c) from Consulta c where c.departamento.id = :departamentoId")
    long countByDepartamentoId(@Param("departamentoId") Long departamentoId);

    /** Usado por el seed para agregar solo las consultas de ejemplo que faltan (el email las identifica). */
    boolean existsByDepartamentoIdAndEmail(Long departamentoId, String email);
}
