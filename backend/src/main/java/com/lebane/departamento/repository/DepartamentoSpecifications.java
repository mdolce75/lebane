package com.lebane.departamento.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Departamento_;
import com.lebane.departamento.entity.Direccion;
import com.lebane.departamento.entity.Direccion_;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.entity.Imagen_;
import com.lebane.departamento.entity.Moneda;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Filtros del listado como {@link Specification} (Criteria API, metamodelo estático). Todos se traducen a predicados
 * SQL sobre columnas indexadas de {@code departamento}; ninguno hace JOIN a colecciones, por lo que el listado y su
 * {@code COUNT} nunca multiplican filas. El filtro por fotos usa {@code EXISTS}, que no multiplica y se resuelve con
 * el índice único {@code (departamento_id, posicion)}.
 */
public final class DepartamentoSpecifications {

    /** Carácter de escape de LIKE; {@link #escaparLike(String)} usa el mismo. */
    static final char ESCAPE = '\\';

    private DepartamentoSpecifications() {
    }

    /**
     * Combina con AND los filtros informados. Siempre separa vigentes de dados de baja: por defecto lista los
     * publicados; con {@code dadosDeBaja}, solo los dados de baja (para reactivarlos).
     */
    public static Specification<Departamento> conFiltro(DepartamentoFiltro filtro) {
        List<Specification<Departamento>> specs = new ArrayList<>();
        specs.add(filtro.dadosDeBaja() ? dadoDeBaja() : noDadoDeBaja());
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
     * {@code lower(titulo) LIKE '%texto%' ESCAPE '\'}, resuelto con el índice GIN de trigramas. Los comodines del
     * usuario ({@code %}, {@code _}) se escapan: buscar "50%" busca el literal. El carácter de escape se declara
     * explícitamente porque, si no, Hibernate genera {@code ESCAPE ''}, que en PostgreSQL desactiva el escape.
     */
    static Specification<Departamento> tituloContiene(String texto) {
        if (texto == null) {
            return null;
        }
        String patron = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(Departamento_.titulo)), patron, ESCAPE);
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

    /**
     * Regla de avisos duplicados: un departamento publicado (no vendido) en la misma unidad física que
     * {@code direccion} (ver {@link Direccion#mismaUbicacion}), sin distinguir mayúsculas. {@code excluirId} deja
     * afuera al propio departamento al editarlo. Con volumen, la resuelve {@code ix_departamento_direccion}.
     */
    public static Specification<Departamento> publicadoEnLaMismaDireccion(Direccion direccion, Long excluirId) {
        return (root, query, cb) -> {
            Path<Direccion> d = root.get(Departamento_.direccion);
            List<Predicate> predicados = new ArrayList<>(List.of(
                    cb.isNull(root.get(Departamento_.fechaBaja)),
                    root.get(Departamento_.estado).in(EstadoDepartamento.activos()),
                    igualSinMayusculas(cb, d.get(Direccion_.calle), direccion.getCalle()),
                    igualSinMayusculas(cb, d.get(Direccion_.numero), direccion.getNumero()),
                    igualSinMayusculas(cb, d.get(Direccion_.piso), direccion.getPiso()),
                    igualSinMayusculas(cb, d.get(Direccion_.unidad), direccion.getUnidad()),
                    igualSinMayusculas(cb, d.get(Direccion_.ciudad), direccion.getCiudad()),
                    igualSinMayusculas(cb, d.get(Direccion_.provincia), direccion.getProvincia())));
            if (excluirId != null) {
                predicados.add(cb.notEqual(root.get(Departamento_.id), excluirId));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    /** {@code lower(columna) = lower(valor)}; un valor ausente solo coincide con {@code NULL}. */
    private static Predicate igualSinMayusculas(CriteriaBuilder cb, Path<String> columna, String valor) {
        return valor == null ? cb.isNull(columna) : cb.equal(cb.lower(columna), valor.toLowerCase(Locale.ROOT));
    }

    /** Código comercial exacto; usa el índice único de {@code codigo}. */
    public static Specification<Departamento> conCodigo(String codigo) {
        return (root, query, cb) -> cb.equal(root.get(Departamento_.codigo), codigo);
    }

    /** Baja lógica: {@code fecha_baja IS NULL}. Usa los índices parciales del listado. */
    public static Specification<Departamento> noDadoDeBaja() {
        return (root, query, cb) -> cb.isNull(root.get(Departamento_.fechaBaja));
    }

    /** {@code fecha_baja IS NOT NULL}. Usa {@code ix_departamento_bajas}. */
    public static Specification<Departamento> dadoDeBaja() {
        return (root, query, cb) -> cb.isNotNull(root.get(Departamento_.fechaBaja));
    }
}
