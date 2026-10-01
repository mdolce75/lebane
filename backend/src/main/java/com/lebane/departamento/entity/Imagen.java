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
 * Metadatos de una foto. El binario vive en el storage de objetos (MinIO), nunca en PostgreSQL; aquí solo se guarda
 * la clave del objeto. La imagen principal es la de menor {@link #posicion}.
 */
@Entity
@Table(name = "imagen")
@EntityListeners(AuditingEntityListener.class)
public class Imagen {

    /** Máximo de fotos por departamento (también garantizado por la base: posicion 0..4 única por departamento). */
    public static final int MAX_POR_DEPARTAMENTO = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "imagen_seq")
    @SequenceGenerator(name = "imagen_seq", sequenceName = "imagen_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departamento_id", nullable = false, updatable = false)
    private Departamento departamento;

    @Column(name = "object_key", nullable = false, unique = true, length = 255, updatable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "posicion", nullable = false)
    private int posicion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Imagen() {
        // JPA
    }

    public Imagen(Departamento departamento, String objectKey, String contentType, long sizeBytes, int posicion) {
        this.departamento = Objects.requireNonNull(departamento, "departamento");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
        this.posicion = posicion;
    }

    public Long getId() {
        return id;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPosicion() {
        return posicion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || effectiveClass(o) != effectiveClass(this)) {
            return false;
        }
        Imagen other = (Imagen) o;
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
