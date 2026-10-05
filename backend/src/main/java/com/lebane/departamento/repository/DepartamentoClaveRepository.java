package com.lebane.departamento.repository;

import java.util.Optional;

/**
 * Consultas por clave que devuelven solo el id (sin cargar la entidad), construidas con la Criteria API. Fragmento de
 * {@link DepartamentoRepository}.
 */
public interface DepartamentoClaveRepository {

    /** Id del departamento con ese código comercial; usa el índice único de {@code codigo}. */
    Optional<Long> findIdByCodigo(String codigo);

    /**
     * Bloquea la fila del departamento ({@code SELECT ... FOR UPDATE}) hasta el fin de la transacción. Serializa las
     * operaciones de un mismo departamento que tienen que ver lo que hizo la anterior (límite de 5 fotos, consultas
     * duplicadas), sin bloquear lecturas ni otros departamentos.
     *
     * <p>Solo bloquea un departamento vigente: si se dio de baja mientras tanto (la baja también toma el lock de la
     * fila), devuelve vacío y la operación responde 409 en lugar de agregarle datos.
     *
     * @return el id si el departamento existe y no fue dado de baja
     */
    Optional<Long> lockById(Long id);
}
