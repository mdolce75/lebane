package com.lebane.departamento.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Departamento_;
import com.lebane.departamento.entity.Direccion_;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Imagen_;
import com.lebane.departamento.entity.Moneda;

import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Filtros del listado como {@link Specification} (Criteria API, metamodelo estático). Todos se traducen a predicados
 * SQL sobre columnas indexadas de {@code departamento}; ninguno hace JOIN a colecciones, por lo que el listado y su
 * {@code COUNT} nunca multiplican filas. El filtro por fotos usa {@code EXISTS}, que no multiplica y se resuelve con
 * el índice único {@code (departamento_id, posicion)}.
 */
public final class DepartamentoSpecifications {

    private DepartamentoSpecifications() {
    }

    /** Combina con AND los filtros informados; sin filtros, no agrega WHERE. */
    public static Specification<Departamento> conFiltro(DepartamentoFiltro filtro) {
        List<Specification<Departamento>> specs = new ArrayList<>();
        add(specs, tituloContiene(filtro.texto()));
        add(specs, ciudadIgual(filtro.ciudad()));
        add(specs, estadoEn(filtro.estados()));
        add(specs, monedaIgual(filtro.moneda()));
        add(specs, precioEntre(filtro.precioMin(), filtro.precioMax()));
        add(specs, alMenos(Departamento_.ambientes, filtro.ambientesMin()));
        add(specs, alMenos(Departamento_.dormitorios, filtro.dormitoriosMin()));
        add(specs, alMenos(Departamento_.banos, filtro.banosMin()));
        add(specs, superficieEntre(filtro.superficieMin(), filtro.superficieMax()));
        add(specs, conImagenes(filtro.conImagenes()));
        return Specification.allOf(specs);
    }

    /**
     * {@code lower(titulo) LIKE '%texto%'}, resuelto con el índice GIN de trigramas. Los comodines del usuario se
     * escapan con la barra invertida (carácter de escape por defecto de PostgreSQL): buscar "50%" busca el literal.
     */
    static Specification<Departamento> tituloContiene(String texto) {
        if (texto == null) {
            return null;
        }
        String patron = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(Departamento_.titulo)), patron);
    }

    /** {@code lower(ciudad) = ?}, resuelto con el índice de expresión {@code lower(ciudad)}. */
    static Specification<Departamento> ciudadIgual(String ciudad) {
        if (ciudad == null) {
            return null;
        }
        String normalizada = ciudad.toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get(Departamento_.direccion).get(Direccion_.ciudad)),
                normalizada);
    }

    static Specification<Departamento> estadoEn(Set<EstadoDepartamento> estados) {
        if (estados == null || estados.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> estados.size() == 1
                ? cb.equal(root.get(Departamento_.estado), estados.iterator().next())
                : root.get(Departamento_.estado).in(estados);
    }

    static Specification<Departamento> monedaIgual(Moneda moneda) {
        if (moneda == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(Departamento_.moneda), moneda);
    }

    static Specification<Departamento> precioEntre(BigDecimal min, BigDecimal max) {
        return rango(Departamento_.precio, min, max);
    }

    static Specification<Departamento> superficieEntre(BigDecimal min, BigDecimal max) {
        return rango(Departamento_.superficieM2, min, max);
    }

    static Specification<Departamento> alMenos(SingularAttribute<Departamento, Integer> atributo,
            Integer minimo) {
        if (minimo == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(atributo), minimo);
    }

    /** {@code [NOT] EXISTS (select 1 from imagen i where i.departamento_id = d.id)}. */
    static Specification<Departamento> conImagenes(Boolean conImagenes) {
        if (conImagenes == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Integer> imagenes = query.subquery(Integer.class);
            var imagen = imagenes.from(Imagen.class);
            imagenes.select(cb.literal(1)).where(cb.equal(imagen.get(Imagen_.departamento), root));
            return conImagenes ? cb.exists(imagenes) : cb.not(cb.exists(imagenes));
        };
    }

    private static Specification<Departamento> rango(SingularAttribute<Departamento, BigDecimal> atributo,
            BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get(atributo), min, max);
            }
            return min != null
                    ? cb.greaterThanOrEqualTo(root.get(atributo), min)
                    : cb.lessThanOrEqualTo(root.get(atributo), max);
        };
    }

    static String escaparLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void add(List<Specification<Departamento>> specs, Specification<Departamento> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
