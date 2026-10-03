package com.lebane.departamento.repository;

import static com.lebane.departamento.repository.ConsultaSpecifications.conEmail;
import static com.lebane.departamento.repository.ConsultaSpecifications.conEmailSinMayusculas;
import static com.lebane.departamento.repository.ConsultaSpecifications.creadaDespuesDe;
import static com.lebane.departamento.repository.ConsultaSpecifications.deDepartamento;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.lebane.departamento.entity.Consulta;

/**
 * Sin consultas escritas como texto ni derivadas del nombre del método: cada una es un método {@code default} sobre
 * {@link ConsultaSpecifications} (Criteria API y metamodelo).
 */
public interface ConsultaRepository extends JpaRepository<Consulta, Long>, JpaSpecificationExecutor<Consulta> {

    /** COUNT en la base usando {@code ix_consulta_departamento}; nunca se cargan las consultas en memoria. */
    default long countByDepartamentoId(Long departamentoId) {
        return count(deDepartamento(departamentoId));
    }

    /** Usado por el seed para agregar solo las consultas de ejemplo que faltan (el email las identifica). */
    default boolean existsByDepartamentoIdAndEmail(Long departamentoId, String email) {
        return exists(deDepartamento(departamentoId).and(conEmail(email)));
    }

    /**
     * Regla de consultas duplicadas: ¿el email ya consultó por el departamento después de {@code desde}? Sin
     * distinguir mayúsculas; con muchas consultas por departamento la resuelve {@code ix_consulta_departamento_email}.
     */
    default boolean existeConsultaDesde(Long departamentoId, String email, Instant desde) {
        return exists(deDepartamento(departamentoId).and(conEmailSinMayusculas(email)).and(creadaDespuesDe(desde)));
    }
}
