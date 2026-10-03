package com.lebane.departamento.repository;

import java.util.Optional;

import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Departamento_;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

/** Criteria API y metamodelo estático: ninguna consulta escrita como texto. */
class DepartamentoClaveRepositoryImpl implements DepartamentoClaveRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Long> findIdByCodigo(String codigo) {
        return primero(entityManager.createQuery(idDonde(Departamento_.codigo, codigo)));
    }

    @Override
    public Optional<Long> lockById(Long id) {
        return primero(entityManager.createQuery(idDonde(Departamento_.id, id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE));
    }

    /**
     * Ambos filtros son claves únicas: a lo sumo una fila. Con {@code getResultList()} y no {@code getResultStream()},
     * porque fuera de una transacción (el seed) la sesión se cierra al volver del método y el stream quedaría leyendo
     * un ResultSet cerrado.
     */
    private static Optional<Long> primero(TypedQuery<Long> query) {
        return query.getResultList().stream().findFirst();
    }

    /** {@code select d.id from Departamento d where d.<atributo> = :valor}. */
    private <T> CriteriaQuery<Long> idDonde(SingularAttribute<Departamento, T> atributo, T valor) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Departamento> departamento = query.from(Departamento.class);
        return query.select(departamento.get(Departamento_.id))
                .where(cb.equal(departamento.get(atributo), valor));
    }
}
