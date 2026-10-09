package com.lebane.departamento.controller;

import static com.lebane.config.OpenApiConfig.BAD_REQUEST;
import static com.lebane.config.OpenApiConfig.CONFLICT;
import static com.lebane.config.OpenApiConfig.INTERNAL_ERROR;
import static com.lebane.config.OpenApiConfig.NOT_FOUND;
import static com.lebane.config.OpenApiConfig.PAYLOAD_TOO_LARGE;
import static com.lebane.config.OpenApiConfig.PRECONDITION_FAILED;
import static com.lebane.config.OpenApiConfig.SERVICE_UNAVAILABLE;
import static com.lebane.config.OpenApiConfig.UNSUPPORTED_MEDIA_TYPE;

import java.net.URI;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lebane.config.OpenApiConfig;
import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.dto.ConsultaResponse;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.service.AltaDepartamentoService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * API de departamentos (v1). Los tags de OpenAPI van por método: las consultas se documentan en su propio grupo.
 */
@RestController
@RequestMapping(path = DepartamentoController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class DepartamentoController {

    static final String BASE_PATH = "/api/departamentos";
    /** Tope de página de consultas: evita OFFSET arbitrariamente profundos. */
    static final int MAX_PAGINA_CONSULTAS = 1000;

    private static final String DESCRIPCION_ALTA = "Da de alta un departamento con sus fotos y responde 202 con el "
            + "recurso creado. Con `multipart/form-data`, la parte `departamento` lleva los datos en JSON "
            + "(`Content-Type: application/json`) y la parte `imagenes` se repite por cada foto (hasta 5; JPEG, PNG o "
            + "WebP, validadas por contenido; la primera es la principal). Todo o nada: si una foto es inválida (400) "
            + "o el storage falla (503), no se crea el departamento ni quedan fotos sueltas. También acepta el JSON "
            + "solo, sin fotos (se pueden agregar después con el endpoint de imágenes). El backend genera el `codigo` "
            + "comercial; si no se indica `estado`, queda DISPONIBLE. No se puede publicar directamente como VENDIDO "
            + "(409 `TRANSICION_DE_ESTADO_INVALIDA`), ni repetir la dirección (calle, número, piso, unidad, ciudad y "
            + "provincia) de otro departamento publicado (409 `AVISO_DUPLICADO`).";

    private static final String ETAG_DESCRIPCION = "Versión del departamento (p. ej. \"3\"). Enviarla en If-Match "
            + "al editar para no pisar cambios de otro usuario.";

    private final DepartamentoService departamentoService;
    private final DepartamentoListadoService listadoService;
    private final ConsultaService consultaService;
    private final AltaDepartamentoService altaService;

    public DepartamentoController(DepartamentoService departamentoService, DepartamentoListadoService listadoService,
            ConsultaService consultaService, AltaDepartamentoService altaService) {
        this.departamentoService = departamentoService;
        this.listadoService = listadoService;
        this.consultaService = consultaService;
        this.altaService = altaService;
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

    /**
     * Alta con fotos (multipart): la parte {@code departamento} lleva los datos en JSON y {@code imagenes}, de 0 a 5
     * fotos. Responde 202 Accepted, como pide el enunciado, con el recurso creado.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Crear un departamento", description = DESCRIPCION_ALTA)
    @ApiResponse(responseCode = "202", description = "Departamento creado (con sus fotos, si se enviaron)",
            headers = {
                @Header(name = HttpHeaders.LOCATION, description = "Ruta relativa del departamento creado",
                        schema = @Schema(type = "string", example = "/api/departamentos/1")),
                @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPCION,
                        schema = @Schema(type = "string", example = "\"0\""))
            })
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "413", ref = PAYLOAD_TOO_LARGE)
    @ApiResponse(responseCode = "415", ref = UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ResponseEntity<DepartamentoDetailResponse> crearConImagenes(
            @Parameter(description = "Datos del departamento, en JSON")
            @Valid @RequestPart("departamento") DepartamentoRequest request,
            @Parameter(description = "Fotos (repetir la parte por cada una, hasta 5)")
            @RequestPart(name = "imagenes", required = false) List<MultipartFile> imagenes) {
        return creado(altaService.crear(request, imagenes));
    }

    /** Alta sin fotos, con el body en JSON. También 202, con el recurso creado. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Crear un departamento", description = DESCRIPCION_ALTA)
    @ApiResponse(responseCode = "202", description = "Departamento creado (con sus fotos, si se enviaron)",
            headers = {
                @Header(name = HttpHeaders.LOCATION, description = "Ruta relativa del departamento creado",
                        schema = @Schema(type = "string", example = "/api/departamentos/1")),
                @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPCION,
                        schema = @Schema(type = "string", example = "\"0\""))
            })
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "415", ref = UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ResponseEntity<DepartamentoDetailResponse> crear(@Valid @RequestBody DepartamentoRequest request) {
        return creado(departamentoService.crear(request));
    }

    private static ResponseEntity<DepartamentoDetailResponse> creado(DepartamentoDetailResponse creado) {
        // Location relativo (RFC 9110): no depende del header Host, que detrás del proxy es el host interno.
        URI location = URI.create(BASE_PATH + "/" + creado.id());
        return ResponseEntity.accepted().location(location).eTag(EntityTags.of(creado.version())).body(creado);
    }

    @GetMapping("/{id}")
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Obtener un departamento",
            description = "Detalle completo: dirección, fotos ordenadas y consultas recibidas (la más reciente primero, con su cantidad). Responde 404 si no existe.")
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
            description = "Baja lógica: el departamento sale del listado (se ve con `dadosDeBaja=true`) y no admite "
                    + "cambios: edición, fotos, consultas y una segunda baja responden 409 `DEPARTAMENTO_DADO_DE_BAJA`. "
                    + "El detalle se sigue pudiendo leer (con `fechaBaja`). El registro se conserva con sus fotos y "
                    + "consultas, y su dirección queda libre para otro aviso. Se puede dar de baja en cualquier "
                    + "estado, también vendido, y se revierte con la reactivación. Enviar en `If-Match` el ETag leído "
                    + "para no dar de baja una versión que otro usuario acaba de modificar (412).")
    @ApiResponse(responseCode = "204", description = "Departamento dado de baja")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
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

    /** Revierte la baja lógica. {@code If-Match} opcional, como en la edición y la baja. */
    @PostMapping("/{id}/reactivacion")
    @Tag(name = OpenApiConfig.TAG_DEPARTAMENTOS)
    @Operation(summary = "Reactivar un departamento dado de baja",
            description = "Vuelve a publicarlo tal como estaba: mismo estado, datos, fotos y consultas. Si está "
                    + "disponible o reservado y mientras tanto se publicó otro departamento en la misma dirección, "
                    + "responde 409 `AVISO_DUPLICADO`. Si no estaba dado de baja, 409 `DEPARTAMENTO_NO_DADO_DE_BAJA`. "
                    + "Sin cuerpo; enviar en `If-Match` el ETag leído (412 si otro usuario lo modificó).")
    @ApiResponse(responseCode = "200", description = "Departamento reactivado",
            headers = @Header(name = HttpHeaders.ETAG, description = "Nueva versión del departamento",
                    schema = @Schema(type = "string", example = "\"5\"")))
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "412", ref = PRECONDITION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ResponseEntity<DepartamentoDetailResponse> reactivar(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long id,
            @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH,
                    description = "ETag leído (p. ej. \"4\"). Opcional: sin él, la reactivación no verifica la versión.",
                    schema = @Schema(type = "string", example = "\"4\""))
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        DepartamentoDetailResponse reactivado = departamentoService.reactivar(id, EntityTags.parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(EntityTags.of(reactivado.version())).body(reactivado);
    }

    @GetMapping("/{id}/consultas")
    @Tag(name = OpenApiConfig.TAG_CONSULTAS)
    @Operation(summary = "Listar las consultas de un departamento",
            description = "Consultas recibidas, de la más reciente a la más antigua, paginadas en la base de datos. "
                    + "Incluye los datos de contacto para responderlas. También para un departamento dado de baja "
                    + "(sus consultas se conservan como historial).")
    @ApiResponse(responseCode = "200", description = "Página de consultas (puede estar vacía)")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public PagedModel<ConsultaResponse> listarConsultas(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long id,
            @Parameter(description = "Página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGINA_CONSULTAS) int page,
            @Parameter(description = "Tamaño de página", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return consultaService.listar(id, page, size);
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
