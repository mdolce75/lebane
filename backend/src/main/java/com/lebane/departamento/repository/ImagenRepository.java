package com.lebane.departamento.repository;

import static com.lebane.departamento.repository.ImagenSpecifications.conId;
import static com.lebane.departamento.repository.ImagenSpecifications.deDepartamento;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Imagen_;

/**
 * Todas las consultas usan el índice único {@code (departamento_id, posicion)}; ninguna hace JOIN a departamento.
 *
 * <p>Sin consultas escritas como texto ni derivadas del nombre del método: cada una es un método {@code default} sobre
 * {@link ImagenSpecifications} (Criteria API y metamodelo).
 */
public interface ImagenRepository extends JpaRepository<Imagen, Long>, JpaSpecificationExecutor<Imagen> {

    /** Imágenes de un departamento ordenadas (la primera es la principal). */
    default List<Imagen> findByDepartamentoIdOrdered(Long departamentoId) {
        return findAll(deDepartamento(departamentoId), JpaSort.of(Imagen_.posicion));
    }

    default long countByDepartamentoId(Long departamentoId) {
        return count(deDepartamento(departamentoId));
    }

    /** Posiciones ocupadas, para asignar la primera libre (0..4). Son a lo sumo 5 filas. */
    default List<Integer> findPosiciones(Long departamentoId) {
        return findByDepartamentoIdOrdered(departamentoId).stream().map(Imagen::getPosicion).toList();
    }

    default Optional<Imagen> findByIdAndDepartamentoId(Long imagenId, Long departamentoId) {
        return findOne(conId(imagenId).and(deDepartamento(departamentoId)));
    }
}
