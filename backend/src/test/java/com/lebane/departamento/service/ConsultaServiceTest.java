package com.lebane.departamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private ConsultaRepository consultaRepository;

    private ConsultaService service;

    @BeforeEach
    void setUp() {
        service = new ConsultaService(departamentoRepository, consultaRepository, new ConsultaMapper());
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
}
