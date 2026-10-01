package com.lebane.departamento.entity;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Consulta (contacto) de un interesado sobre un departamento. Contiene datos personales: no se registran en logs ni
 * se devuelven en las respuestas de la API pública.
 */
@Entity
@Table(name = "consulta")
@EntityListeners(AuditingEntityListener.class)
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "consulta_seq")
    @SequenceGenerator(name = "consulta_seq", sequenceName = "consulta_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departamento_id", nullable = false, updatable = false)
    private Departamento departamento;

    @Column(name = "nombre", nullable = false, length = 100, updatable = false)
    private String nombre;

    @Column(name = "email", nullable = false, length = 254, updatable = false)
    private String email;

    @Column(name = "telefono", length = 30, updatable = false)
    private String telefono;

    @Column(name = "mensaje", nullable = false, length = 2000, updatable = false)
    private String mensaje;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Consulta() {
        // JPA
    }

    public Consulta(Departamento departamento, String nombre, String email, String telefono, String mensaje) {
        this.departamento = Objects.requireNonNull(departamento, "departamento");
        this.nombre = nombre;
        this.email = email;
        this.telefono = telefono;
        this.mensaje = mensaje;
    }

    public Long getId() {
        return id;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        // Sin datos personales.
        return "Consulta[id=" + id + "]";
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || effectiveClass(o) != effectiveClass(this)) {
            return false;
        }
        Consulta other = (Consulta) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }
}
