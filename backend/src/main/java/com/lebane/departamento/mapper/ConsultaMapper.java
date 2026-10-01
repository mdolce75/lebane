package com.lebane.departamento.mapper;

import static com.lebane.departamento.mapper.Textos.opcional;
import static com.lebane.departamento.mapper.Textos.requerido;

import org.springframework.stereotype.Component;

import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.entity.Consulta;
import com.lebane.departamento.entity.Departamento;

@Component
public class ConsultaMapper {

    public Consulta toEntity(Departamento departamento, ConsultaRequest request) {
        return new Consulta(departamento, requerido(request.nombre()), requerido(request.email()),
                opcional(request.telefono()), requerido(request.mensaje()));
    }

    public ConsultaCreatedResponse toCreatedResponse(Consulta consulta, Long departamentoId) {
        return new ConsultaCreatedResponse(consulta.getId(), departamentoId, consulta.getCreatedAt());
    }
}
