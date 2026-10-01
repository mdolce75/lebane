package com.lebane.departamento.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lebane.departamento.entity.Imagen;

/** Todas las consultas usan el índice único {@code (departamento_id, posicion)}; ninguna hace JOIN a departamento. */
public interface ImagenRepository extends JpaRepository<Imagen, Long> {

    /** Imágenes de un departamento ordenadas (la primera es la principal). */
    @Query("select i from Imagen i where i.departamento.id = :departamentoId order by i.posicion asc")
    List<Imagen> findByDepartamentoIdOrdered(@Param("departamentoId") Long departamentoId);

    @Query("select count(i) from Imagen i where i.departamento.id = :departamentoId")
    long countByDepartamentoId(@Param("departamentoId") Long departamentoId);

    /** Posiciones ocupadas, para asignar la primera libre (0..4). */
    @Query("select i.posicion from Imagen i where i.departamento.id = :departamentoId order by i.posicion")
    List<Integer> findPosiciones(@Param("departamentoId") Long departamentoId);

    @Query("select i from Imagen i where i.id = :imagenId and i.departamento.id = :departamentoId")
    Optional<Imagen> findByIdAndDepartamentoId(@Param("imagenId") Long imagenId,
            @Param("departamentoId") Long departamentoId);
}
