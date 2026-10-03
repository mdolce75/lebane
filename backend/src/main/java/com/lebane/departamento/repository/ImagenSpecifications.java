package com.lebane.departamento.repository;

import org.springframework.data.jpa.domain.Specification;

import com.lebane.departamento.entity.Departamento_;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Imagen_;

/**
 * Predicados de {@link Imagen} con la Criteria API y el metamodelo estático. {@code imagen.departamento.id} se resuelve
 * con la columna FK, sin JOIN a {@code departamento}, y usa el índice único {@code (departamento_id, posicion)}.
 */
public final class ImagenSpecifications {

    private ImagenSpecifications() {
    }

    public static Specification<Imagen> deDepartamento(Long departamentoId) {
        return (root, query, cb) -> cb.equal(root.get(Imagen_.departamento).get(Departamento_.id), departamentoId);
    }

    public static Specification<Imagen> conId(Long imagenId) {
        return (root, query, cb) -> cb.equal(root.get(Imagen_.id), imagenId);
    }
}
