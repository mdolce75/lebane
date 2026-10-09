package com.lebane.departamento.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.lebane.departamento.entity.Consulta;
import com.lebane.departamento.entity.Consulta_;
import com.lebane.departamento.entity.Departamento_;

/**
 * Predicados de {@link Consulta} con la Criteria API y el metamodelo estático. {@code consulta.departamento.id} se
 * resuelve con la columna FK, sin JOIN a {@code departamento}.
 */
public final class ConsultaSpecifications {

    private ConsultaSpecifications() {
    }

    /** Usa {@code ix_consulta_departamento_fecha}. */
    public static Specification<Consulta> deDepartamento(Long departamentoId) {
        return (root, query, cb) -> cb.equal(root.get(Consulta_.departamento).get(Departamento_.id), departamentoId);
    }

    /** Email exacto (el seed identifica sus consultas de ejemplo así). */
    public static Specification<Consulta> conEmail(String email) {
        return (root, query, cb) -> cb.equal(root.get(Consulta_.email), email);
    }

    /**
     * Email sin distinguir mayúsculas: {@code upper(email) = upper(:email)}, la misma expresión que
     * {@code ix_consulta_departamento_email}.
     */
    public static Specification<Consulta> conEmailSinMayusculas(String email) {
        return (root, query, cb) -> cb.equal(cb.upper(root.get(Consulta_.email)), cb.upper(cb.literal(email)));
    }

    public static Specification<Consulta> creadaDespuesDe(Instant desde) {
        return (root, query, cb) -> cb.greaterThan(root.get(Consulta_.createdAt), desde);
    }
}
