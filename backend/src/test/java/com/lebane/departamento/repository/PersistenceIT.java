package com.lebane.departamento.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lebane.config.JpaConfig;
import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.entity.Consulta;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.ConsultaMapper;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.storage.TestStorageProperties;
import com.lebane.storage.service.PublicBucketImageUrlResolver;
import com.lebane.support.PostgresContainer;

import jakarta.persistence.EntityManager;

/**
 * Persistencia contra PostgreSQL real: migraciones Flyway + validación del esquema por Hibernate (si las entidades
 * no coincidieran con las tablas, el contexto no levantaría), auditoría, concurrencia optimista, restricciones de
 * integridad en la base y relaciones LAZY.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportTestcontainers(PostgresContainer.class)
@Import(JpaConfig.class)
class PersistenceIT {

    private final DepartamentoMapper mapper = new DepartamentoMapper(
            new PublicBucketImageUrlResolver(TestStorageProperties.of("http://localhost:9000", "bucket")));

    @Autowired
    private DepartamentoRepository departamentoRepository;
    @Autowired
    private ImagenRepository imagenRepository;
    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedTheInitialMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void persistsWithAuditDatesAndInitialVersion() {
        Departamento saved = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(departamentoRepository.existsByCodigo(saved.getCodigo())).isTrue();
    }

    @Test
    void updateIncrementsVersion() {
        Departamento saved = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));

        saved.cambiarEstado(EstadoDepartamento.RESERVADO);
        departamentoRepository.flush();

        assertThat(saved.getVersion()).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicateCodigo() {
        Departamento first = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));
        Departamento duplicate = mapper.toNewEntity(TestFixtures.departamento(), first.getCodigo());

        assertThatThrownBy(() -> departamentoRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Defensa en profundidad: la regla dormitorios < ambientes también la garantiza un CHECK. */
    @Test
    void databaseRejectsMoreBedroomsThanRooms() {
        Departamento invalid = mapper.toNewEntity(TestFixtures.departamento(2, 2, null), codigo());

        assertThatThrownBy(() -> departamentoRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** El máximo de 5 fotos lo garantiza la base: posición 0..4 y única por departamento. */
    @Test
    void databaseRejectsSixthImagePosition() {
        Departamento departamento = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));

        assertThatThrownBy(() -> imagenRepository.saveAndFlush(imagen(departamento, 5)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateImagePosition() {
        Departamento departamento = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));
        imagenRepository.saveAndFlush(imagen(departamento, 0));

        assertThatThrownBy(() -> imagenRepository.saveAndFlush(imagen(departamento, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void imagesAreReturnedInPositionOrderWithLazyDepartamento() {
        Departamento departamento = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));
        imagenRepository.save(imagen(departamento, 2));
        imagenRepository.save(imagen(departamento, 0));
        imagenRepository.save(imagen(departamento, 1));
        entityManager.flush();
        entityManager.clear();

        var imagenes = imagenRepository.findByDepartamentoIdOrdered(departamento.getId());

        assertThat(imagenes).extracting(Imagen::getPosicion).containsExactly(0, 1, 2);
        assertThat(imagenes).allSatisfy(imagen ->
                assertThat(Hibernate.isInitialized(imagen.getDepartamento())).isFalse());
    }

    @Test
    void consultaKeepsDepartamentoLazyAndIsCountedInDatabase() {
        Departamento departamento = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));
        Departamento otro = departamentoRepository.saveAndFlush(nuevo(EstadoDepartamento.DISPONIBLE));
        ConsultaMapper consultaMapper = new ConsultaMapper();
        Consulta consulta = consultaRepository.save(consultaMapper.toEntity(departamento, TestFixtures.consulta()));
        consultaRepository.save(consultaMapper.toEntity(departamento, TestFixtures.consulta()));
        consultaRepository.save(consultaMapper.toEntity(otro, TestFixtures.consulta()));
        entityManager.flush();
        entityManager.clear();

        Consulta reloaded = consultaRepository.findById(consulta.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(reloaded.getDepartamento())).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(consultaRepository.countByDepartamentoId(departamento.getId())).isEqualTo(2);
        assertThat(consultaRepository.countByDepartamentoId(otro.getId())).isEqualTo(1);
    }

    private Departamento nuevo(EstadoDepartamento estado) {
        return mapper.toNewEntity(TestFixtures.departamento(3, 2, estado), codigo());
    }

    private static Imagen imagen(Departamento departamento, int posicion) {
        return new Imagen(departamento, "departamentos/" + UUID.randomUUID() + ".jpg", "image/jpeg", 1024, posicion);
    }

    private static String codigo() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
