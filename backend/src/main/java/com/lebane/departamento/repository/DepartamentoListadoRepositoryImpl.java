package com.lebane.departamento.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import com.lebane.departamento.entity.Consulta;
import com.lebane.departamento.entity.Consulta_;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Departamento_;
import com.lebane.departamento.entity.Direccion;
import com.lebane.departamento.entity.Direccion_;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Imagen_;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Consultas del listado construidas con la Criteria API y el metamodelo estático ({@code Departamento_}, ...): ninguna
 * consulta escrita como texto (ni JPQL ni SQL) y ningún nombre de atributo en strings. Un renombre en las entidades
 * rompe la compilación, no la ejecución.
 */
class DepartamentoListadoRepositoryImpl implements DepartamentoListadoRepository {

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

    /**
     * Agregados de una página en <b>una sola</b> sentencia: por cada departamento de la página, subconsultas
     * escalares correlacionadas cuentan sus fotos y consultas y obtienen la foto principal (la de menor posición).
     *
     * <p>Cada subconsulta se resuelve con un índice ({@code uk_imagen_departamento_posicion} para fotos y foto
     * principal, {@code ix_consulta_departamento} para consultas) y se evalúa solo para las filas de la página
     * (como máximo 100), así que el costo no depende del tamaño de las tablas. No se hace JOIN entre {@code imagen}
     * y {@code consulta}: sin producto fotos × consultas, los {@code COUNT} son exactos sin {@code DISTINCT}.
     * Como la posición es única por departamento, la foto principal es a lo sumo una.
     */
    @Override
    public Map<Long, DepartamentoAgregados> agregados(Collection<Long> departamentoIds) {
        if (departamentoIds.isEmpty()) {
            return Map.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Departamento> departamento = query.from(Departamento.class);

        Subquery<Long> cantidadImagenes = query.subquery(Long.class);
        Root<Imagen> imagen = cantidadImagenes.from(Imagen.class);
        // count(posicion) y count(departamento_id): columnas NOT NULL cubiertas por los índices (index-only scan); count(id)
        // obligaría a leer cada fila de la tabla.
        cantidadImagenes.select(cb.count(imagen.get(Imagen_.posicion)))
                .where(cb.equal(imagen.get(Imagen_.departamento), departamento));

        Subquery<Integer> primeraPosicion = query.subquery(Integer.class);
        Root<Imagen> otraImagen = primeraPosicion.from(Imagen.class);
        primeraPosicion.select(cb.min(otraImagen.get(Imagen_.posicion)))
                .where(cb.equal(otraImagen.get(Imagen_.departamento), departamento));

        Subquery<String> imagenPrincipal = query.subquery(String.class);
        Root<Imagen> principal = imagenPrincipal.from(Imagen.class);
        imagenPrincipal.select(principal.get(Imagen_.objectKey))
                .where(cb.equal(principal.get(Imagen_.departamento), departamento),
                        cb.equal(principal.get(Imagen_.posicion), primeraPosicion));

        Subquery<Long> cantidadConsultas = query.subquery(Long.class);
        Root<Consulta> consulta = cantidadConsultas.from(Consulta.class);
        cantidadConsultas.select(cb.count(consulta.get(Consulta_.departamento)))
                .where(cb.equal(consulta.get(Consulta_.departamento), departamento));

        Path<Long> id = departamento.get(Departamento_.id);
        query.multiselect(id, cantidadImagenes, imagenPrincipal, cantidadConsultas)
                .where(id.in(departamentoIds));

        List<Tuple> rows = entityManager.createQuery(query).getResultList();
        Map<Long, DepartamentoAgregados> agregados = HashMap.newHashMap(rows.size());
        for (Tuple row : rows) {
            agregados.put(row.get(id), new DepartamentoAgregados(
                    row.get(cantidadImagenes), row.get(imagenPrincipal), row.get(cantidadConsultas)));
        }
        return agregados;
    }
}
