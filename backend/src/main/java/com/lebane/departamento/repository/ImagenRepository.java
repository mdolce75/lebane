package com.lebane.departamento.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lebane.departamento.entity.Imagen;

public interface ImagenRepository extends JpaRepository<Imagen, Long> {

    /**
     * Imágenes de un departamento ordenadas (la primera es la principal). Una sola consulta por la FK, resuelta con
     * el índice único {@code (departamento_id, posicion)}, sin JOIN a {@code departamento}.
     */
    @Query("select i from Imagen i where i.departamento.id = :departamentoId order by i.posicion asc")
    List<Imagen> findByDepartamentoIdOrdered(@Param("departamentoId") Long departamentoId);
}
