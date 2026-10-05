package com.lebane.departamento.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.entity.Consulta;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.mapper.ConsultaMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.ErrorCode;

/**
 * Registro de consultas de interesados. Los datos personales nunca se registran en logs.
 *
 * <p>Reglas: un departamento vendido no recibe consultas, y el mismo email no consulta dos veces por el mismo
 * departamento en {@link #VENTANA_DUPLICADOS} (evita dobles envíos y spam).
 */
@Service
public class ConsultaService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaService.class);
    static final Duration VENTANA_DUPLICADOS = Duration.ofHours(24);

    private final DepartamentoRepository departamentoRepository;
    private final ConsultaRepository consultaRepository;
    private final ConsultaMapper mapper;
    private final Clock clock;

    public ConsultaService(DepartamentoRepository departamentoRepository, ConsultaRepository consultaRepository,
            ConsultaMapper mapper, Clock clock) {
        this.departamentoRepository = departamentoRepository;
        this.consultaRepository = consultaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ConsultaCreatedResponse crear(Long departamentoId, ConsultaRequest request) {
        Departamento departamento = DepartamentoService.vigente(departamentoRepository.findById(departamentoId));
        if (!departamento.getEstado().aceptaConsultas()) {
            throw new BusinessRuleException(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE,
                    "El departamento ya no está disponible y no recibe nuevas consultas");
        }
        // Bloquea la fila del departamento hasta el commit: dos envíos simultáneos (doble click, reintento) se
        // serializan y el segundo ve la consulta del primero.
        departamentoRepository.lockById(departamentoId)
                .orElseThrow(DepartamentoService::dadoDeBaja);
        Consulta nueva = mapper.toEntity(departamento, request);
        if (consultaRepository.existeConsultaDesde(departamentoId,
                nueva.getEmail(), clock.instant().minus(VENTANA_DUPLICADOS))) {
            throw new BusinessRuleException(ErrorCode.CONSULTA_DUPLICADA,
                    "Ya recibimos una consulta con este email por este departamento en las últimas 24 horas");
        }
        Consulta consulta = consultaRepository.saveAndFlush(nueva);
        log.info("Consulta registrada", kv("departamentoId", departamentoId), kv("consultaId", consulta.getId()));
        return mapper.toCreatedResponse(consulta, departamentoId);
    }
}
