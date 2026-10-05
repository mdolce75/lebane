package com.lebane.departamento.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Departamento publicado.
 *
 * <p>Sin colecciones mapeadas ({@code imagenes}, {@code consultas}): {@link Imagen} y {@link Consulta} referencian
 * al departamento con {@code @ManyToOne(fetch = LAZY)}. Así ninguna operación puede disparar la carga accidental de
 * una colección (N+1 o paginación en memoria); las imágenes y los contadores se obtienen con consultas dedicadas.
 */
@Entity
@Table(name = "departamento")
@EntityListeners(AuditingEntityListener.class)
public class Departamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "departamento_seq")
    @SequenceGenerator(name = "departamento_seq", sequenceName = "departamento_seq", allocationSize = 50)
    private Long id;

    /** Código de negocio único e inmutable (referencia comercial y clave natural del seed). */
    @Column(name = "codigo", nullable = false, unique = true, length = 20, updatable = false)
    private String codigo;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "descripcion", length = 4000)
    private String descripcion;

    @Column(name = "precio", nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(name = "moneda", nullable = false, length = 3)
    private Moneda moneda;

    @Column(name = "ambientes", nullable = false)
    private int ambientes;

    @Column(name = "dormitorios", nullable = false)
    private int dormitorios;

    @Column(name = "banos", nullable = false)
    private int banos;

    @Column(name = "superficie_m2", nullable = false, precision = 8, scale = 2)
    private BigDecimal superficieM2;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoDepartamento estado;

    @Embedded
    private Direccion direccion;

    /** Baja lógica: cuándo se dio de baja; {@code null} mientras está publicado. */
    @Column(name = "fecha_baja")
    private Instant fechaBaja;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Departamento() {
        // JPA
    }

    public Departamento(String codigo, EstadoDepartamento estado) {
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.estado = Objects.requireNonNull(estado, "estado");
    }

    /** Reemplaza los datos editables. Las reglas se validan en el DTO y se garantizan con CHECKs en la base. */
    public void actualizarDatos(String titulo, String descripcion, BigDecimal precio, Moneda moneda, int ambientes,
            int dormitorios, int banos, BigDecimal superficieM2, Direccion direccion) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.precio = precio;
        this.moneda = moneda;
        this.ambientes = ambientes;
        this.dormitorios = dormitorios;
        this.banos = banos;
        this.superficieM2 = superficieM2;
        this.direccion = direccion;
    }

    public void cambiarEstado(EstadoDepartamento nuevoEstado) {
        this.estado = Objects.requireNonNull(nuevoEstado, "estado");
    }

    /**
     * Baja lógica: el departamento sale del listado y no admite cambios (edición, fotos ni consultas), pero el registro
     * se conserva con sus fotos y consultas. Se revierte con {@link #reactivar()}.
     */
    public void darDeBaja(Instant fecha) {
        if (estaDadoDeBaja()) {
            throw new IllegalStateException("El departamento " + codigo + " ya fue dado de baja");
        }
        this.fechaBaja = Objects.requireNonNull(fecha, "fecha");
    }

    /** Vuelve a publicarlo tal como estaba (mismo estado, datos, fotos y consultas). */
    public void reactivar() {
        if (!estaDadoDeBaja()) {
            throw new IllegalStateException("El departamento " + codigo + " no está dado de baja");
        }
        this.fechaBaja = null;
    }

    public boolean estaDadoDeBaja() {
        return fechaBaja != null;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public int getAmbientes() {
        return ambientes;
    }

    public int getDormitorios() {
        return dormitorios;
    }

    public int getBanos() {
        return banos;
    }

    public BigDecimal getSuperficieM2() {
        return superficieM2;
    }

    public EstadoDepartamento getEstado() {
        return estado;
    }

    public Direccion getDireccion() {
        return direccion;
    }

    public Instant getFechaBaja() {
        return fechaBaja;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Igualdad por identidad persistente, segura con proxies de Hibernate.
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || effectiveClass(o) != effectiveClass(this)) {
            return false;
        }
        Departamento other = (Departamento) o;
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
