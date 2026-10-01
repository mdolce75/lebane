package com.lebane.departamento.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
import com.lebane.exception.ResourceNotFoundException;

/** Registro de consultas de interesados. Los datos personales nunca se registran en logs. */
@Service
public class ConsultaService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaService.class);

    private final DepartamentoRepository departamentoRepository;
    private final ConsultaRepository consultaRepository;
    private final ConsultaMapper mapper;

    public ConsultaService(DepartamentoRepository departamentoRepository, ConsultaRepository consultaRepository,
            ConsultaMapper mapper) {
        this.departamentoRepository = departamentoRepository;
        this.consultaRepository = consultaRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ConsultaCreatedResponse crear(Long departamentoId, ConsultaRequest request) {
        Departamento departamento = departamentoRepository.findById(departamentoId)
                .orElseThrow(() -> new ResourceNotFoundException(DepartamentoService.RECURSO));
        if (!departamento.getEstado().aceptaConsultas()) {
            throw new BusinessRuleException(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE,
                    "El departamento ya no está disponible y no recibe nuevas consultas");
        }
        Consulta consulta = consultaRepository.saveAndFlush(mapper.toEntity(departamento, request));
        log.info("Consulta registrada", kv("departamentoId", departamentoId), kv("consultaId", consulta.getId()));
        return mapper.toCreatedResponse(consulta, departamentoId);
    }
}
