package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.ErrorCode;
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

    private static final Instant AHORA = Instant.parse("2026-10-05T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AHORA, ZoneOffset.UTC);

    private final DepartamentoMapper mapper = new DepartamentoMapper(
            new PublicBucketImageUrlResolver(TestStorageProperties.of("http://localhost:9000", "bucket")));
    private DepartamentoService service;

    @BeforeEach
    void setUp() {
        service = new DepartamentoService(departamentoRepository, imagenRepository, consultaRepository, mapper,
                codigoGenerator, CLOCK);
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

    @Test
    void crearComoVendidoEsUnaTransicionInvalidaYNoGuardaNada() {
        assertThatThrownBy(() -> service.crear(TestFixtures.departamento(3, 2, EstadoDepartamento.VENDIDO)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.TRANSICION_DE_ESTADO_INVALIDA);
        verifyNoInteractions(departamentoRepository, codigoGenerator);
    }

    @Test
    void crearComoReservadoEstaPermitido() {
        when(codigoGenerator.generate()).thenReturn("DEP-RESERVAD");
        when(departamentoRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.crear(TestFixtures.departamento(3, 2, EstadoDepartamento.RESERVADO)).estado())
                .isEqualTo(EstadoDepartamento.RESERVADO);
    }

    @Test
    void unDepartamentoVendidoNoSeModificaNiSinCambiarElEstado() {
        Departamento vendido = persisted(5L, 2L);
        vendido.cambiarEstado(EstadoDepartamento.VENDIDO);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(vendido));

        for (EstadoDepartamento nuevo : new EstadoDepartamento[] {null, EstadoDepartamento.VENDIDO,
                EstadoDepartamento.DISPONIBLE}) {
            assertThatThrownBy(() -> service.actualizar(5L, TestFixtures.departamento(4, 3, nuevo), Set.of()))
                    .as("estado nuevo %s", nuevo)
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE);
        }
        assertThat(vendido.getAmbientes()).isEqualTo(3);
        assertThat(vendido.getEstado()).isEqualTo(EstadoDepartamento.VENDIDO);
        verify(departamentoRepository, never()).flush();
    }

    @Test
    void laVersionDesactualizadaSeInformaAntesQueLaReglaDeVendido() {
        Departamento vendido = persisted(5L, 2L);
        vendido.cambiarEstado(EstadoDepartamento.VENDIDO);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(vendido));

        // 412 primero: el cliente recarga y ve el estado actual (vendido) en lugar de un error sin contexto.
        assertThatThrownBy(() -> service.actualizar(5L, TestFixtures.departamento(), Set.of(1L)))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void lasTransicionesPermitidasSeAplican() {
        Departamento departamento = persisted(5L, 2L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));

        for (EstadoDepartamento destino : new EstadoDepartamento[] {EstadoDepartamento.RESERVADO,
                EstadoDepartamento.DISPONIBLE, EstadoDepartamento.RESERVADO, EstadoDepartamento.VENDIDO}) {
            assertThat(service.actualizar(5L, TestFixtures.departamento(3, 2, destino), Set.of()).estado())
                    .isEqualTo(destino);
        }
    }

    @Test
    void crearEnLaDireccionDeOtroPublicadoEsDuplicadoYNoGuardaNada() {
        when(codigoGenerator.generate()).thenReturn("DEP-ABCDEFGH");
        when(departamentoRepository.exists(cualquierSpec())).thenReturn(true);

        assertThatThrownBy(() -> service.crear(TestFixtures.departamento()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AVISO_DUPLICADO));
        verify(departamentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void editarSinCambiarLaDireccionNoBuscaDuplicados() {
        // Un aviso cargado antes de la regla, con la dirección repetida, sigue siendo editable.
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(persisted(5L, 2L)));

        service.actualizar(5L, TestFixtures.departamento(4, 3, null), Set.of());

        verify(departamentoRepository, never()).exists(cualquierSpec());
    }

    @Test
    void editarHaciaLaDireccionDeOtroPublicadoEsDuplicadoYNoCambiaNada() {
        Departamento departamento = persisted(5L, 2L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));
        when(departamentoRepository.exists(cualquierSpec())).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(5L, enLaUnidad(TestFixtures.departamento(), "C"), Set.of()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AVISO_DUPLICADO));
        assertThat(departamento.getDireccion().getUnidad()).isEqualTo("B");
        verify(departamentoRepository, never()).flush();
    }

    @Test
    void editarHaciaUnaDireccionLibreSeAplica() {
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(persisted(5L, 2L)));

        DepartamentoDetailResponse response = service.actualizar(5L, enLaUnidad(TestFixtures.departamento(), "C"),
                Set.of());

        assertThat(response.direccion().unidad()).isEqualTo("C");
        verify(departamentoRepository).exists(cualquierSpec());
    }

    @Test
    void darDeBajaGuardaLaFechaSinBorrarNada() {
        Departamento departamento = persisted(5L, 2L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));

        service.darDeBaja(5L, Set.of(2L));

        assertThat(departamento.getFechaBaja()).isEqualTo(AHORA);
        verify(departamentoRepository).flush();
        verify(departamentoRepository, never()).delete(any(Departamento.class));
        verifyNoInteractions(imagenRepository, consultaRepository);
    }

    @Test
    void darDeBajaConVersionDesactualizadaEs412YNoCambiaNada() {
        Departamento departamento = persisted(5L, 2L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(departamento));

        assertThatThrownBy(() -> service.darDeBaja(5L, Set.of(1L))).isInstanceOf(PreconditionFailedException.class);
        assertThat(departamento.estaDadoDeBaja()).isFalse();
    }

    @Test
    void unVendidoTambienSePuedeDarDeBaja() {
        Departamento vendido = persisted(5L, 2L);
        vendido.cambiarEstado(EstadoDepartamento.VENDIDO);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(vendido));

        service.darDeBaja(5L, Set.of());

        assertThat(vendido.estaDadoDeBaja()).isTrue();
    }

    @Test
    void unDepartamentoDadoDeBajaSeLeePeroNoSeModifica() {
        Departamento dadoDeBaja = persisted(5L, 3L);
        dadoDeBaja.darDeBaja(AHORA);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(dadoDeBaja));

        assertThat(service.obtenerDetalle(5L).fechaBaja()).isEqualTo(AHORA);
        assertDadoDeBaja(() -> service.actualizar(5L, TestFixtures.departamento(), Set.of()));
        assertDadoDeBaja(() -> service.darDeBaja(5L, Set.of()));
        verify(departamentoRepository, never()).flush();
    }

    @Test
    void laVersionDesactualizadaSeInformaAntesQueLaBaja() {
        Departamento dadoDeBaja = persisted(5L, 3L);
        dadoDeBaja.darDeBaja(AHORA);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(dadoDeBaja));

        assertThatThrownBy(() -> service.actualizar(5L, TestFixtures.departamento(), Set.of(1L)))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void reactivarLoVuelveAPublicarSiLaDireccionEstaLibre() {
        Departamento dadoDeBaja = persisted(5L, 3L);
        dadoDeBaja.darDeBaja(AHORA);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(dadoDeBaja));

        DepartamentoDetailResponse response = service.reactivar(5L, Set.of(3L));

        assertThat(response.fechaBaja()).isNull();
        assertThat(dadoDeBaja.estaDadoDeBaja()).isFalse();
        verify(departamentoRepository).exists(cualquierSpec());
        verify(departamentoRepository).flush();
    }

    @Test
    void reactivarConLaDireccionOcupadaEsDuplicadoYSigueDeBaja() {
        Departamento dadoDeBaja = persisted(5L, 3L);
        dadoDeBaja.darDeBaja(AHORA);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(dadoDeBaja));
        when(departamentoRepository.exists(cualquierSpec())).thenReturn(true);

        assertThatThrownBy(() -> service.reactivar(5L, Set.of()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AVISO_DUPLICADO));
        assertThat(dadoDeBaja.estaDadoDeBaja()).isTrue();
    }

    @Test
    void unVendidoSeReactivaSinControlarLaDireccion() {
        Departamento vendido = persisted(5L, 3L);
        vendido.cambiarEstado(EstadoDepartamento.VENDIDO);
        vendido.darDeBaja(AHORA);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(vendido));

        service.reactivar(5L, Set.of());

        assertThat(vendido.estaDadoDeBaja()).isFalse();
        verify(departamentoRepository, never()).exists(cualquierSpec());
    }

    @Test
    void reactivarUnoPublicadoOConVersionViejaNoCambiaNada() {
        Departamento publicado = persisted(5L, 3L);
        when(departamentoRepository.findById(5L)).thenReturn(Optional.of(publicado));

        assertThatThrownBy(() -> service.reactivar(5L, Set.of()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEPARTAMENTO_NO_DADO_DE_BAJA));
        publicado.darDeBaja(AHORA);
        assertThatThrownBy(() -> service.reactivar(5L, Set.of(1L))).isInstanceOf(PreconditionFailedException.class);
        assertThat(publicado.estaDadoDeBaja()).isTrue();
        verify(departamentoRepository, never()).flush();
    }

    private static void assertDadoDeBaja(ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEPARTAMENTO_DADO_DE_BAJA));
    }

    private static Specification<Departamento> cualquierSpec() {
        return ArgumentMatchers.<Specification<Departamento>>any();
    }

    private static DepartamentoRequest enLaUnidad(DepartamentoRequest r, String unidad) {
        DireccionRequest d = r.direccion();
        return new DepartamentoRequest(r.titulo(), r.descripcion(), r.precio(), r.moneda(), r.ambientes(),
                r.dormitorios(), r.banos(), r.superficieM2(), r.estado(), new DireccionRequest(d.calle(), d.numero(),
                        d.piso(), unidad, d.ciudad(), d.provincia(), d.codigoPostal(), d.latitud(), d.longitud(),
                        d.placeId()));
    }

    private Departamento persisted(Long id, long version) {
        Departamento departamento = mapper.toNewEntity(TestFixtures.departamento(), "DEP-EXISTING");
        ReflectionTestUtils.setField(departamento, "id", id);
        ReflectionTestUtils.setField(departamento, "version", version);
        return departamento;
    }
}
