package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.lebane.departamento.dto.ImagenResponse;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.DependencyUnavailableException;
import com.lebane.exception.ErrorCode;
import com.lebane.exception.InvalidRequestException;
import com.lebane.exception.PayloadTooLargeException;
import com.lebane.exception.ResourceNotFoundException;
import com.lebane.storage.TestImages;
import com.lebane.storage.TestStorageProperties;
import com.lebane.storage.service.ObjectStorageService;
import com.lebane.storage.service.PublicBucketImageUrlResolver;

class ImagenServiceTest {

    private static final long ID = 7L;

    private final DepartamentoRepository departamentoRepository = mock(DepartamentoRepository.class);
    private final ImagenRepository imagenRepository = mock(ImagenRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private ImagenService service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ImagenService(departamentoRepository, imagenRepository, storage,
                new DepartamentoMapper(new PublicBucketImageUrlResolver(
                        TestStorageProperties.of("http://cdn", "bucket"))),
                new TransactionTemplate(transactionManager), TestStorageProperties.of("http://cdn", "bucket"));
        when(departamentoRepository.findById(ID))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE)));
        when(departamentoRepository.lockById(ID)).thenReturn(Optional.of(ID));
        when(departamentoRepository.getReferenceById(ID))
                .thenReturn(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE));
        when(imagenRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Imagen imagen = invocation.getArgument(0);
            ReflectionTestUtils.setField(imagen, "id", 99L);
            return imagen;
        });
    }

    @Test
    void uploadsDetectedTypeAndRegistersFirstFreePosition() {
        when(imagenRepository.countByDepartamentoId(ID)).thenReturn(2L);
        when(imagenRepository.findPosiciones(ID)).thenReturn(List.of(0, 2));

        // Declarado como cualquier cosa: el tipo sale del contenido (PNG).
        ImagenResponse response = service.subir(ID, png(), 1024);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), any(), eq(1024L), eq("image/png"));
        assertThat(key.getValue()).matches("^departamentos/7/[0-9a-f-]{36}\\.png$");
        assertThat(response.posicion()).isEqualTo(1);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.url()).isEqualTo("http://cdn/bucket/" + key.getValue());
    }

    @Test
    void unDepartamentoDadoDeBajaNoRecibeFotosNiTocaElStorage() {
        Departamento dadoDeBaja = new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE);
        dadoDeBaja.darDeBaja(Instant.parse("2026-10-05T12:00:00Z"));
        when(departamentoRepository.findById(ID)).thenReturn(Optional.of(dadoDeBaja));

        assertThatThrownBy(() -> service.subir(ID, png(), 1024)).isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void missingDepartamentoFailsBeforeTouchingStorage() {
        when(departamentoRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subir(ID, png(), 1024)).isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void rejectsEmptyOversizedAndNonImageFiles() {
        assertThatThrownBy(() -> service.subir(ID, png(), 0))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsKey("archivo"));
        assertThatThrownBy(() -> service.subir(ID, png(), 5L * 1024 * 1024 + 1))
                .isInstanceOf(PayloadTooLargeException.class);
        ByteArrayResource html = new ByteArrayResource("<html>no soy una foto</html>"
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.subir(ID, html, 28))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.getFieldErrors())
                        .containsEntry("archivo", "debe ser una imagen JPEG, PNG o WebP"));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void limitIsCheckedBeforeUploading() {
        when(imagenRepository.countByDepartamentoId(ID)).thenReturn(5L);

        assertThatThrownBy(() -> service.subir(ID, png(), 1024))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LIMITE_IMAGENES_ALCANZADO));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    /** Dos subidas concurrentes: la segunda encuentra el límite al registrar bajo lock y deshace su subida. */
    @Test
    void limitReachedUnderLockCompensatesTheUpload() {
        when(imagenRepository.countByDepartamentoId(ID)).thenReturn(4L);
        when(imagenRepository.findPosiciones(ID)).thenReturn(List.of(0, 1, 2, 3, 4));

        assertThatThrownBy(() -> service.subir(ID, png(), 1024)).isInstanceOf(BusinessRuleException.class);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), any(), anyLong(), anyString());
        verify(storage).deleteCompensating(key.getValue());
    }

    @Test
    void databaseFailureAfterUploadCompensates() {
        when(imagenRepository.findPosiciones(ID)).thenReturn(List.of());
        doThrow(new DataIntegrityViolationException("boom")).when(imagenRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.subir(ID, png(), 1024)).isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), any(), anyLong(), anyString());
        verify(storage).deleteCompensating(key.getValue());
    }

    @Test
    void storageFailureDoesNotRegisterAnything() {
        doThrow(new DependencyUnavailableException(ErrorCode.STORAGE_UNAVAILABLE, "no", null))
                .when(storage).upload(anyString(), any(), anyLong(), anyString());

        assertThatThrownBy(() -> service.subir(ID, png(), 1024)).isInstanceOf(DependencyUnavailableException.class);
        verify(imagenRepository, never()).saveAndFlush(any());
        verify(storage, never()).deleteCompensating(anyString());
    }

    @Test
    void deleteRemovesTheRowFirstAndThenTheObject() {
        Imagen imagen = new Imagen(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE), "departamentos/7/a.png",
                "image/png", 10, 0);
        when(imagenRepository.findByIdAndDepartamentoId(3L, ID)).thenReturn(Optional.of(imagen));

        service.eliminar(ID, 3L);

        InOrder order = inOrder(imagenRepository, storage);
        order.verify(imagenRepository).delete(imagen);
        order.verify(storage).delete("departamentos/7/a.png");
    }

    @Test
    void deleteSucceedsEvenIfStorageIsDown() {
        Imagen imagen = new Imagen(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE), "departamentos/7/a.png",
                "image/png", 10, 0);
        when(imagenRepository.findByIdAndDepartamentoId(3L, ID)).thenReturn(Optional.of(imagen));
        doThrow(new DependencyUnavailableException(ErrorCode.STORAGE_UNAVAILABLE, "no", null))
                .when(storage).delete(anyString());

        service.eliminar(ID, 3L);

        verify(imagenRepository).delete(imagen);
    }

    @Test
    void deleteOfUnknownImageIsNotFound() {
        when(imagenRepository.findByIdAndDepartamentoId(3L, ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(ID, 3L)).isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void unDepartamentoVendidoNoAdmiteFotosNuevasYNoTocaElStorage() {
        when(departamentoRepository.findById(ID))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.VENDIDO)));

        assertThatThrownBy(() -> service.subir(ID, png(), 1024))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE);
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
        verify(imagenRepository, never()).saveAndFlush(any());
    }

    @Test
    void unDepartamentoVendidoNoPermiteEliminarFotos() {
        Imagen imagen = new Imagen(new Departamento("DEP-X", EstadoDepartamento.VENDIDO), "departamentos/7/a.png",
                "image/png", 10, 0);
        when(imagenRepository.findByIdAndDepartamentoId(3L, ID)).thenReturn(Optional.of(imagen));

        assertThatThrownBy(() -> service.eliminar(ID, 3L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE);
        verify(imagenRepository, never()).delete(any(Imagen.class));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void unDepartamentoReservadoSiAdmiteCambiosEnSusFotos() {
        when(departamentoRepository.findById(ID))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.RESERVADO)));
        when(imagenRepository.findPosiciones(ID)).thenReturn(List.of());

        assertThat(service.subir(ID, png(), 1024).posicion()).isZero();
    }

    private static ByteArrayResource png() {
        return new ByteArrayResource(TestImages.pngLike(1024));
    }
}
