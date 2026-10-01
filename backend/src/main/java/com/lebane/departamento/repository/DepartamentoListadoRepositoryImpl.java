package com.lebane.departamento.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Departamento_;
import com.lebane.departamento.entity.Direccion;
import com.lebane.departamento.entity.Direccion_;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class DepartamentoListadoRepositoryImpl implements DepartamentoListadoRepository {

    /**
     * Agregados de una página, pre-agregados por tabla antes del JOIN.
     *
     * <p>Unir {@code departamento} con {@code imagen} y {@code consulta} y luego agrupar multiplicaría las filas
     * (fotos × consultas por departamento) y obligaría a {@code COUNT(DISTINCT ...)} sobre ese producto. Acá cada
     * tabla se agrupa por separado, restringida a los IDs de la página (índices {@code uk_imagen_departamento_posicion}
     * e {@code ix_consulta_departamento}), y recién después se une: a lo sumo una fila por departamento, por lo que
     * {@code COUNT(*)} es exacto. La imagen principal es la de menor posición; la posición es única por
     * departamento, así que el JOIN devuelve como máximo una.
     *
     * <p>SQL nativo justificado: las tablas derivadas en el FROM (subconsultas agregadas unidas con LEFT JOIN) no
     * existen en JPQL ni en la Criteria API estándar. La consulta es fija (sin texto del usuario) y sus parámetros se
     * enlazan como valores.
     */
    static final String AGREGADOS_SQL = """
            SELECT d.id,
                   COALESCE(img.total, 0) AS cantidad_imagenes,
                   principal.object_key   AS imagen_principal,
                   COALESCE(con.total, 0) AS cantidad_consultas
              FROM departamento d
              LEFT JOIN (SELECT departamento_id, COUNT(*) AS total, MIN(posicion) AS primera
                           FROM imagen
                          WHERE departamento_id IN (:ids)
                          GROUP BY departamento_id) img ON img.departamento_id = d.id
              LEFT JOIN imagen principal
                     ON principal.departamento_id = d.id AND principal.posicion = img.primera
              LEFT JOIN (SELECT departamento_id, COUNT(*) AS total
                           FROM consulta
                          WHERE departamento_id IN (:ids)
                          GROUP BY departamento_id) con ON con.departamento_id = d.id
             WHERE d.id IN (:ids)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<DepartamentoListadoRow> buscarPagina(Specification<Departamento> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DepartamentoListadoRow> query = cb.createQuery(DepartamentoListadoRow.class);
        Root<Departamento> departamento = query.from(Departamento.class);
        Path<Direccion> direccion = departamento.get(Departamento_.direccion);

        query.select(cb.construct(DepartamentoListadoRow.class,
                departamento.get(Departamento_.id),
                departamento.get(Departamento_.codigo),
                departamento.get(Departamento_.titulo),
                departamento.get(Departamento_.precio),
                departamento.get(Departamento_.moneda),
                departamento.get(Departamento_.ambientes),
                departamento.get(Departamento_.dormitorios),
                departamento.get(Departamento_.banos),
                departamento.get(Departamento_.superficieM2),
                departamento.get(Departamento_.estado),
                direccion.get(Direccion_.ciudad),
                direccion.get(Direccion_.provincia),
                departamento.get(Departamento_.createdAt)));

        Predicate where = spec == null ? null : spec.toPredicate(departamento, query, cb);
        if (where != null) {
            query.where(where);
        }
        query.orderBy(QueryUtils.toOrders(pageable.getSort(), departamento, cb));

        return entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }

    @Override
    public Map<Long, DepartamentoAgregados> agregados(Collection<Long> departamentoIds) {
        if (departamentoIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(AGREGADOS_SQL)
                .setParameter("ids", departamentoIds)
                .getResultList();
        Map<Long, DepartamentoAgregados> agregados = HashMap.newHashMap(rows.size());
        for (Object[] row : rows) {
            agregados.put(((Number) row[0]).longValue(), new DepartamentoAgregados(
                    ((Number) row[1]).longValue(), (String) row[2], ((Number) row[3]).longValue()));
        }
        return agregados;
    }
}
