package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.exception.PreconditionFailedException;
import com.lebane.exception.ResourceNotFoundException;
import com.lebane.storage.TestStorageProperties;
import com.lebane.storage.service.PublicBucketImageUrlResolver;

@ExtendWith(MockitoExtension.class)
class DepartamentoServiceTest {

    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private ImagenRepository imagenRepository;
    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private CodigoDepartamentoGenerator codigoGenerator;

    private final DepartamentoMapper mapper = new DepartamentoMapper(
            new PublicBucketImageUrlResolver(TestStorageProperties.of("http://localhost:9000", "bucket")));
    private DepartamentoService service;

    @BeforeEach
    void setUp() {
        service = new DepartamentoService(departamentoRepository, imagenRepository, consultaRepository, mapper,
                codigoGenerator);
    }

    @Test
    void crearAssignsGeneratedCodeAndDefaultState() {
        when(codigoGenerator.generate()).thenReturn("DEP-ABCDEFGH");
        when(departamentoRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Departamento saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        DepartamentoDetailResponse response = service.crear(TestFixtures.departamento(3, 2, null));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.codigo()).isEqualTo("DEP-ABCDEFGH");
        assertThat(response.estado()).isEqualTo(EstadoDepartamento.DISPONIBLE);
        assertThat(response.imagenes()).isEmpty();
        assertThat(response.cantidadConsultas()).isZero();
        // Recién creado: no consulta imágenes ni consultas.
        verifyNoInteractions(imagenRepository, consultaRepository);
    }

    @Test
    void obtenerDetalleUsesDedicatedQueries() {
        Departamento departamento = persisted(5L, 2L);
        Imagen imagen = new Imagen(departamento, "departamentos/5/a.jpg", "image/jpeg", 100, 0);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));
        when(imagenRepository.findByDepartamentoIdOrdered(5L)).thenReturn(List.of(imagen));
        when(consultaRepository.countByDepartamentoId(5L)).thenReturn(4L);

        DepartamentoDetailResponse response = service.obtenerDetalle(5L);

        assertThat(response.imagenes()).hasSize(1);
        assertThat(response.cantidadConsultas()).isEqualTo(4L);
        assertThat(response.version()).isEqualTo(2L);
    }

    @Test
    void obtenerDetalleOfMissingDepartamentoIsNotFound() {
        when(departamentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerDetalle(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void actualizarWithMatchingVersionAppliesChanges() {
        Departamento departamento = persisted(5L, 2L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));

        DepartamentoDetailResponse response = service.actualizar(5L,
                TestFixtures.departamento(4, 3, EstadoDepartamento.RESERVADO), Set.of(2L));

        assertThat(response.ambientes()).isEqualTo(4);
        assertThat(response.estado()).isEqualTo(EstadoDepartamento.RESERVADO);
        verify(departamentoRepository).flush();
    }

    @Test
    void actualizarWithoutPreconditionAppliesChanges() {
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(persisted(5L, 2L)));

        assertThat(service.actualizar(5L, TestFixtures.departamento(4, 3, null), Set.of()).ambientes()).isEqualTo(4);
    }

    @Test
    void actualizarWithStaleVersionFailsWithoutChanges() {
        Departamento departamento = persisted(5L, 2L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));

        assertThatThrownBy(() -> service.actualizar(5L, TestFixtures.departamento(4, 3, null), Set.of(1L)))
                .isInstanceOf(PreconditionFailedException.class);
        assertThat(departamento.getAmbientes()).isEqualTo(3);
        verify(departamentoRepository, never()).flush();
    }

    @Test
    void actualizarMissingDepartamentoIsNotFound() {
        when(departamentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(99L, TestFixtures.departamento(), Set.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Departamento persisted(Long id, long version) {
        Departamento departamento = mapper.toNewEntity(TestFixtures.departamento(), "DEP-EXISTING");
        ReflectionTestUtils.setField(departamento, "id", id);
        ReflectionTestUtils.setField(departamento, "version", version);
        return departamento;
    }
}
