package com.lebane.departamento.controller;

import static com.lebane.config.OpenApiConfig.BAD_REQUEST;
import static com.lebane.config.OpenApiConfig.CONFLICT;
import static com.lebane.config.OpenApiConfig.INTERNAL_ERROR;
import static com.lebane.config.OpenApiConfig.NOT_FOUND;
import static com.lebane.config.OpenApiConfig.PRECONDITION_FAILED;
import static com.lebane.config.OpenApiConfig.SERVICE_UNAVAILABLE;
import static com.lebane.config.OpenApiConfig.UNSUPPORTED_MEDIA_TYPE;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import com.lebane.config.OpenApiConfig;
import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.service.ConsultaService;
import com.lebane.departamento.service.DepartamentoListadoService;
import com.lebane.departamento.service.DepartamentoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * API de departamentos (v1). Los tags de OpenAPI van por método: las consultas se documentan en su propio grupo.
 */
@RestController
@RequestMapping(path = DepartamentoController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class DepartamentoController {

    static final String BASE_PATH = "/api/v1/departamentos";

    private static final String ETAG_DESCRIPCION = "Versión del departamento (p. ej. \"3\"). Enviarla en If-Match "
            + "al editar para no pisar cambios de otro usuario.";

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
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Listar departamentos",
            description = "Listado paginado con filtros y orden, resueltos en la base de datos (nunca en memoria). "
                    + "Cada ítem trae su foto principal y la cantidad de fotos y de consultas. Los filtros se "
                    + "combinan con AND; los valores de `estado` se combinan con OR.")
    @ApiResponse(responseCode = "200", description = "Página de resultados (puede estar vacía)")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public PagedModel<DepartamentoListItemResponse> listar(
            @ParameterObject @Valid @ModelAttribute DepartamentoListadoParams params) {
        return listadoService.listar(params);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Crear un departamento",
            description = "Da de alta un departamento. El backend genera el `codigo` comercial; si no se indica "
                    + "`estado`, queda DISPONIBLE. No se puede publicar directamente como VENDIDO (409 "
                    + "`TRANSICION_DE_ESTADO_INVALIDA`), ni repetir la dirección (calle, número, piso, unidad, ciudad y provincia) de "
                    + "otro departamento publicado (409 `AVISO_DUPLICADO`). Las fotos se suben después, de a una, con el endpoint de "
                    + "imágenes.")
    @ApiResponse(responseCode = "201", description = "Departamento creado",
            headers = {
                @Header(name = HttpHeaders.LOCATION, description = "Ruta relativa del departamento creado",
                        schema = @Schema(type = "string", example = "/api/v1/departamentos/1")),
                @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPCION,
                        schema = @Schema(type = "string", example = "\"0\""))
            })
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "415", ref = UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ResponseEntity<DepartamentoDetailResponse> crear(@Valid @RequestBody DepartamentoRequest request) {
        DepartamentoDetailResponse creado = departamentoService.crear(request);
        // Location relativo (RFC 9110): no depende del header Host, que detrás del proxy es el host interno.
        URI location = URI.create(BASE_PATH + "/" + creado.id());
        return ResponseEntity.created(location).eTag(EntityTags.of(creado.version())).body(creado);
    }

    @GetMapping("/{id}")
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Obtener un departamento",
            description = "Detalle completo: dirección, fotos ordenadas y cantidad de consultas.")
    @ApiResponse(responseCode = "200", description = "Departamento encontrado",
            headers = @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPCION,
                    schema = @Schema(type = "string", example = "\"3\"")))
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ResponseEntity<DepartamentoDetailResponse> obtener(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long id) {
        DepartamentoDetailResponse detalle = departamentoService.obtenerDetalle(id);
        return ResponseEntity.ok().eTag(EntityTags.of(detalle.version())).body(detalle);
    }

    /**
     * Reemplazo completo. {@code If-Match} es opcional: si se envía (ETag del GET), la edición se rechaza con 412
     * cuando otro usuario modificó el departamento desde esa lectura.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Editar un departamento",
            description = "Reemplazo completo de los datos (no modifica fotos ni consultas). Enviar en `If-Match` el "
                    + "ETag obtenido al leerlo: si otro usuario lo modificó desde entonces, responde 412 en lugar de "
                    + "pisar sus cambios. Reglas de estado: DISPONIBLE ⇄ RESERVADO y ambos → VENDIDO; un departamento "
                    + "VENDIDO no se puede modificar (409 `DEPARTAMENTO_NO_DISPONIBLE`) y un cambio de estado no "
                    + "permitido responde 409 `TRANSICION_DE_ESTADO_INVALIDA`. Si cambia la dirección, no puede "
                    + "coincidir con la de otro departamento publicado (409 `AVISO_DUPLICADO`).")
    @ApiResponse(responseCode = "200", description = "Departamento actualizado",
            headers = @Header(name = HttpHeaders.ETAG, description = "Nueva versión del departamento",
                    schema = @Schema(type = "string", example = "\"4\"")))
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "412", ref = PRECONDITION_FAILED)
    @ApiResponse(responseCode = "415", ref = UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ResponseEntity<DepartamentoDetailResponse> actualizar(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long id,
            @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH,
                    description = "ETag leído (p. ej. \"3\"). Opcional: sin él, la edición no verifica la versión.",
                    schema = @Schema(type = "string", example = "\"3\""))
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody DepartamentoRequest request) {
        DepartamentoDetailResponse actualizado =
                departamentoService.actualizar(id, request, EntityTags.parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(EntityTags.of(actualizado.version())).body(actualizado);
    }

    /**
     * Baja lógica. {@code If-Match} es opcional, como en la edición: si se envía y otro usuario modificó el
     * departamento desde la lectura, 412 sin darlo de baja.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Dar de baja un departamento",
            description = "Baja lógica: el departamento deja de aparecer en el listado y responde 404 en el detalle, "
                    + "la edición, las fotos y las consultas. El registro se conserva en la base con sus fotos y "
                    + "consultas, como historial, y su dirección queda libre para otro aviso. Se puede dar de baja en "
                    + "cualquier estado, también vendido. Es definitiva: una segunda baja responde 404. Enviar en "
                    + "`If-Match` el ETag leído para no dar de baja una versión que otro usuario acaba de modificar "
                    + "(412).")
    @ApiResponse(responseCode = "204", description = "Departamento dado de baja")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "412", ref = PRECONDITION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public void darDeBaja(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long id,
            @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH,
                    description = "ETag leído (p. ej. \"3\"). Opcional: sin él, la baja no verifica la versión.",
                    schema = @Schema(type = "string", example = "\"3\""))
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        departamentoService.darDeBaja(id, EntityTags.parseIfMatch(ifMatch));
    }

    @PostMapping(path = "/{id}/consultas", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = OpenApiConfig.TAG_CONSULTAS)
    @Operation(summary = "Enviar una consulta",
            description = "Registra la consulta de un interesado. Un departamento VENDIDO no recibe consultas (409 "
                    + "`DEPARTAMENTO_NO_DISPONIBLE`), y el mismo email no puede consultar dos veces por el mismo departamento "
                    + "en 24 horas (409 `CONSULTA_DUPLICADA`). La respuesta no devuelve los datos personales "
                    + "enviados.")
    @ApiResponse(responseCode = "201", description = "Consulta registrada")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "415", ref = UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ConsultaCreatedResponse crearConsulta(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long id,
            @Valid @RequestBody ConsultaRequest request) {
        return consultaService.crear(id, request);
    }
}
