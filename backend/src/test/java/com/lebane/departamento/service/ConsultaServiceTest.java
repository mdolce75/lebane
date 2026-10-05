package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.entity.Consulta;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.mapper.ConsultaMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.ErrorCode;
import com.lebane.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private ConsultaRepository consultaRepository;

    private ConsultaService service;

    @BeforeEach
    void setUp() {
        service = new ConsultaService(departamentoRepository, consultaRepository, new ConsultaMapper(), CLOCK);
        lenient().when(departamentoRepository.lockById(7L)).thenReturn(Optional.of(7L));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoDepartamento.class, names = {"DISPONIBLE", "RESERVADO"})
    void registersConsultaForAvailableDepartamento(EstadoDepartamento estado) {
        when(departamentoRepository.findById(7L)).thenReturn(Optional.of(new Departamento("DEP-X", estado)));
        when(consultaRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Consulta consulta = invocation.getArgument(0);
            ReflectionTestUtils.setField(consulta, "id", 3L);
            return consulta;
        });

        ConsultaCreatedResponse response = service.crear(7L, TestFixtures.consulta());

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.departamentoId()).isEqualTo(7L);
    }

    @Test
    void rejectsConsultaForSoldDepartamento() {
        when(departamentoRepository.findById(7L))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.VENDIDO)));

        assertThatThrownBy(() -> service.crear(7L, TestFixtures.consulta()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE));
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsConsultaForMissingDepartamento() {
        when(departamentoRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(7L, TestFixtures.consulta()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rechazaElMismoEmailSobreElMismoDepartamentoEnLas24Horas() {
        when(departamentoRepository.findById(7L))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE)));
        when(consultaRepository.existeConsultaDesde(7L,
                "ana.perez@example.com", Instant.parse("2026-10-02T12:00:00Z"))).thenReturn(true);

        assertThatThrownBy(() -> service.crear(7L, TestFixtures.consulta()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONSULTA_DUPLICADA));
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    @Test
    void bloqueaElDepartamentoAntesDeBuscarDuplicadosYGuardar() {
        when(departamentoRepository.findById(7L))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE)));
        when(consultaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.crear(7L, TestFixtures.consulta());

        // Dos envíos simultáneos se serializan con el lock: el segundo ve la consulta del primero.
        InOrder orden = inOrder(departamentoRepository, consultaRepository);
        orden.verify(departamentoRepository).lockById(7L);
        orden.verify(consultaRepository).existeConsultaDesde(7L,
                "ana.perez@example.com", AHORA.minus(ConsultaService.VENTANA_DUPLICADOS));
        orden.verify(consultaRepository).saveAndFlush(any());
    }

    @Test
    void unDepartamentoDadoDeBajaNoRecibeConsultas() {
        Departamento dadoDeBaja = new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE);
        dadoDeBaja.darDeBaja(AHORA);
        when(departamentoRepository.findById(7L)).thenReturn(Optional.of(dadoDeBaja));

        assertThatThrownBy(() -> service.crear(7L, TestFixtures.consulta()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    @Test
    void siSeDioDeBajaMientrasTantoElLockNoLoEncuentraYNoGuarda() {
        when(departamentoRepository.findById(7L))
                .thenReturn(Optional.of(new Departamento("DEP-X", EstadoDepartamento.DISPONIBLE)));
        when(departamentoRepository.lockById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(7L, TestFixtures.consulta()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(consultaRepository, never()).saveAndFlush(any());
    }
}
