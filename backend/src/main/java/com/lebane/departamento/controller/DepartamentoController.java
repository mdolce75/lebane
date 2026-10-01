package com.lebane.departamento.controller;

import java.net.URI;

import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.service.ConsultaService;
import com.lebane.departamento.service.DepartamentoListadoService;
import com.lebane.departamento.service.DepartamentoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * API de departamentos (v1). Las imágenes se agregan en la Fase 4.
 */
@RestController
@RequestMapping(path = DepartamentoController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class DepartamentoController {

    static final String BASE_PATH = "/api/v1/departamentos";

    private final DepartamentoService departamentoService;
    private final DepartamentoListadoService listadoService;
    private final ConsultaService consultaService;

    public DepartamentoController(DepartamentoService departamentoService, DepartamentoListadoService listadoService,
            ConsultaService consultaService) {
        this.departamentoService = departamentoService;
        this.listadoService = listadoService;
        this.consultaService = consultaService;
    }

    /**
     * Listado paginado con filtros y orden, resuelto en PostgreSQL. Respuesta en el formato estándar de Spring Data
     * ({@code content} + {@code page: {size, number, totalElements, totalPages}}).
     */
    @GetMapping
    public PagedModel<DepartamentoListItemResponse> listar(@Valid @ModelAttribute DepartamentoListadoParams params) {
        return listadoService.listar(params);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DepartamentoDetailResponse> crear(@Valid @RequestBody DepartamentoRequest request) {
        DepartamentoDetailResponse creado = departamentoService.crear(request);
        // Location relativo (RFC 9110): no depende del header Host, que detrás del proxy es el host interno.
        URI location = URI.create(BASE_PATH + "/" + creado.id());
        return ResponseEntity.created(location).eTag(EntityTags.of(creado.version())).body(creado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartamentoDetailResponse> obtener(@PathVariable @Positive Long id) {
        DepartamentoDetailResponse detalle = departamentoService.obtenerDetalle(id);
        return ResponseEntity.ok().eTag(EntityTags.of(detalle.version())).body(detalle);
    }

    /**
     * Reemplazo completo. {@code If-Match} es opcional: si se envía (ETag del GET), la edición se rechaza con 412
     * cuando otro usuario modificó el departamento desde esa lectura.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DepartamentoDetailResponse> actualizar(@PathVariable @Positive Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody DepartamentoRequest request) {
        DepartamentoDetailResponse actualizado =
                departamentoService.actualizar(id, request, EntityTags.parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(EntityTags.of(actualizado.version())).body(actualizado);
    }

    @PostMapping(path = "/{id}/consultas", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaCreatedResponse crearConsulta(@PathVariable @Positive Long id,
            @Valid @RequestBody ConsultaRequest request) {
        return consultaService.crear(id, request);
    }
}
