package com.lebane.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lebane.exception.ApiError;
import com.lebane.exception.ErrorCode;
import com.lebane.logging.RequestContext;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Documentación OpenAPI de la API de dominio (springdoc). El contrato se genera desde los controllers y las
 * anotaciones de los DTOs; las restricciones de Bean Validation se traducen solas (required, min/max, pattern...).
 * El spec queda versionado en {@code docs/openapi.json} y {@code OpenApiSpecTest} falla si se desactualiza.
 *
 * <p>Las respuestas de error son componentes reutilizables ({@code #/components/responses/...}) con el esquema
 * {@link ApiError} y ejemplos con los mensajes reales; cada operación referencia solo las que puede devolver.
 */
@Configuration
public class OpenApiConfig {

    public static final String TAG_DEPARTAMENTOS = "Departamentos";
    public static final String TAG_IMAGENES = "Imágenes";
    public static final String TAG_CONSULTAS = "Consultas";
    public static final String TAG_DIRECCIONES = "Direcciones";

    /** Referencias a las respuestas de error reutilizables, para usar en {@code @ApiResponse(ref = ...)}. */
    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";
    public static final String PRECONDITION_FAILED = "#/components/responses/PreconditionFailed";
    public static final String PAYLOAD_TOO_LARGE = "#/components/responses/PayloadTooLarge";
    public static final String UNSUPPORTED_MEDIA_TYPE = "#/components/responses/UnsupportedMediaType";
    public static final String INTERNAL_ERROR = "#/components/responses/InternalError";
    public static final String SERVICE_UNAVAILABLE = "#/components/responses/ServiceUnavailable";

    private static final String API_ERROR_REF = "#/components/schemas/ApiError";
    private static final String REQUEST_ID_EJEMPLO = "7f3c2a9e-req-42";

    private static final String DESCRIPCION = """
            API REST de Lebane para gestionar departamentos en venta: alta, edición, listado con filtros, fotos y \
            consultas de interesados.

            **Convenciones**

            - **Errores**: toda respuesta 4xx/5xx tiene el mismo esquema `ApiError`, con un código estable en \
            `error`, un mensaje apto para mostrar al usuario y el `requestId` para soporte. Nunca incluye stack \
            traces, SQL ni detalles de infraestructura. Los errores de validación detallan cada campo en \
            `fieldErrors` (las claves son las rutas del body o los nombres de los parámetros, p. ej. \
            `direccion.ciudad`).
            - **Correlation ID**: cada request puede enviar `X-Request-Id`; si no lo envía o no es válido, el \
            backend genera uno. La respuesta siempre lo devuelve y queda en todos los logs del request.
            - **Concurrencia optimista**: el detalle, el alta y la edición devuelven `ETag` con la versión del \
            departamento. Enviarlo en `If-Match` al editar evita pisar cambios de otro usuario (412 si cambió).
            - **Paginación**: `page` (desde 0) y `size` (1 a 100), con una ventana máxima de 10.000 resultados por \
            búsqueda. La respuesta usa el formato estándar de Spring Data (`content` + `page`).
            - **Disponibilidad**: si una dependencia no crítica falla (storage de imágenes, proveedor de \
            direcciones), solo se degradan las operaciones que la usan: 503 `STORAGE_UNAVAILABLE` al subir fotos, \
            respuesta `degradado: true` en el autocompletado. El resto de la API sigue funcionando.
            """;

    @Bean
    OpenAPI lebaneOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
        errores().forEach(components::addResponses);
        return new OpenAPI()
                .info(new Info()
                        .title("Lebane API")
                        // Versión del contrato (la del path /api/v1), no la del artefacto: el spec versionado no
                        // cambia con cada build.
                        .version("v1")
                        .description(DESCRIPCION))
                // Relativo: funciona igual directo contra el backend que detrás de nginx (/api en el mismo origen).
                .servers(List.of(new Server().url("/").description("Mismo origen que la documentación")))
                .tags(List.of(
                        new Tag().name(TAG_DEPARTAMENTOS).description("Alta, detalle, edición y listado con filtros"),
                        new Tag().name(TAG_IMAGENES).description("Fotos de un departamento (hasta 5, en MinIO)"),
                        new Tag().name(TAG_CONSULTAS).description("Consultas de interesados sobre un departamento"),
                        new Tag().name(TAG_DIRECCIONES).description("Autocompletado de direcciones")))
                .components(components);
    }

    /** {@code X-Request-Id}: header opcional en todos los requests y presente en todas las respuestas. */
    @Bean
    OpenApiCustomizer requestIdCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            operation.addParametersItem(new HeaderParameter()
                    .name(RequestContext.REQUEST_ID_HEADER)
                    .required(false)
                    .description("Correlation ID del request (1 a 128 caracteres: letras, números, '.', '_', ':' "
                            + "o '-'). Si falta o no es válido, el backend genera un UUID.")
                    .schema(new StringSchema().pattern("^[A-Za-z0-9._:\\-]{1,128}$").example(REQUEST_ID_EJEMPLO)));
            // Las respuestas de error son referencias ($ref) a componentes: el header va en el componente.
            operation.getResponses().values().stream()
                    .filter(response -> response.get$ref() == null)
                    .forEach(OpenApiConfig::agregarHeaderRequestId);
        }));
    }

    /**
     * Descripciones del formato de página de Spring Data ({@code PagedModel} y {@code PageMetadata}), que no se
     * pueden anotar porque son clases de la librería.
     */
    @Bean
    OpenApiCustomizer paginaCustomizer() {
        Map<String, String> metadata = Map.of(
                "size", "Tamaño de página pedido",
                "number", "Número de página (desde 0)",
                "totalElements", "Total de resultados que cumplen los filtros",
                "totalPages", "Total de páginas");
        return openApi -> openApi.getComponents().getSchemas().forEach((nombre, schema) -> {
            if (nombre.equals("PageMetadata")) {
                schema.setDescription("Datos de paginación");
                metadata.forEach((propiedad, descripcion) -> {
                    Schema<?> p = (Schema<?>) schema.getProperties().get(propiedad);
                    if (p != null) {
                        p.setDescription(descripcion);
                    }
                });
            } else if (nombre.startsWith("PagedModel")) {
                schema.setDescription("Página de resultados (formato estándar de Spring Data)");
                Schema<?> content = (Schema<?>) schema.getProperties().get("content");
                if (content != null) {
                    content.setDescription("Resultados de la página, en el orden pedido");
                }
            }
        });
    }

    private static Map<String, ApiResponse> errores() {
        Map<String, ApiResponse> errores = new LinkedHashMap<>();
        errores.put("BadRequest", error("Datos inválidos: body, parámetros o archivo. Cada campo con error se "
                        + "detalla en `fieldErrors`.",
                ejemplo("validacion", "Validación de campos", 400, ErrorCode.VALIDATION_ERROR,
                        "La solicitud contiene datos inválidos", "/api/v1/departamentos",
                        Map.of("titulo", "no debe estar vacío", "dormitorios",
                                "debe ser menor que la cantidad de ambientes")),
                ejemplo("json", "JSON mal formado", 400, ErrorCode.BAD_REQUEST,
                        "El cuerpo de la solicitud no es JSON válido", "/api/v1/departamentos", Map.of())));
        errores.put("NotFound", error("El recurso no existe.",
                ejemplo("departamento", "Departamento inexistente", 404, ErrorCode.NOT_FOUND,
                        "No se encontró el departamento solicitado", "/api/v1/departamentos/999", Map.of())));
        errores.put("Conflict", error("La operación entra en conflicto con el estado actual (ver `error`).",
                ejemplo("limite", "Límite de fotos alcanzado", 409, ErrorCode.LIMITE_IMAGENES_ALCANZADO,
                        "El departamento ya tiene el máximo de 5 fotos", "/api/v1/departamentos/7/imagenes",
                        Map.of()),
                ejemplo("vendido", "Departamento vendido", 409, ErrorCode.DEPARTAMENTO_NO_DISPONIBLE,
                        "El departamento ya no está disponible y no recibe nuevas consultas",
                        "/api/v1/departamentos/7/consultas", Map.of()),
                ejemplo("concurrencia", "Modificación simultánea", 409, ErrorCode.CONCURRENT_MODIFICATION,
                        "El recurso fue modificado por otra operación; recargalo y volvé a intentar",
                        "/api/v1/departamentos/7", Map.of())));
        errores.put("PreconditionFailed", error("`If-Match` no coincide con la versión actual: otro usuario "
                        + "modificó el recurso desde la lectura. Volver a leerlo (GET) y reintentar.",
                ejemplo("version", "Versión desactualizada", 412, ErrorCode.PRECONDITION_FAILED,
                        "El recurso fue modificado desde la última lectura; recargalo y volvé a intentar",
                        "/api/v1/departamentos/7", Map.of())));
        errores.put("PayloadTooLarge", error("El archivo o el request supera el tamaño máximo.",
                ejemplo("imagen", "Imagen demasiado grande", 413, ErrorCode.PAYLOAD_TOO_LARGE,
                        "La imagen supera el tamaño máximo de 5 MB", "/api/v1/departamentos/7/imagenes", Map.of())));
        errores.put("UnsupportedMediaType", error("`Content-Type` no admitido por el endpoint.",
                ejemplo("tipo", "Tipo de contenido no soportado", 415, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Tipo de contenido no soportado", "/api/v1/departamentos", Map.of())));
        errores.put("InternalError", error("Error inesperado. El mensaje es genérico; el `requestId` permite "
                        + "encontrar el detalle en los logs.",
                ejemplo("inesperado", "Error inesperado", 500, ErrorCode.INTERNAL_ERROR,
                        "Ocurrió un error inesperado; si persiste, informá el requestId", "/api/v1/departamentos",
                        Map.of())));
        errores.put("ServiceUnavailable", error("Una dependencia necesaria no está disponible (base de datos o, al "
                        + "subir fotos, el storage). Reintentar más tarde.",
                ejemplo("base", "Base de datos no disponible", 503, ErrorCode.SERVICE_UNAVAILABLE,
                        "El servicio no está disponible temporalmente; intentá nuevamente", "/api/v1/departamentos",
                        Map.of()),
                ejemplo("storage", "Storage de imágenes no disponible", 503, ErrorCode.STORAGE_UNAVAILABLE,
                        "El servicio de imágenes no está disponible; intentá nuevamente en unos minutos",
                        "/api/v1/departamentos/7/imagenes", Map.of())));
        errores.values().forEach(OpenApiConfig::agregarHeaderRequestId);
        return errores;
    }

    @SafeVarargs
    private static ApiResponse error(String descripcion, Map.Entry<String, Example>... ejemplos) {
        MediaType mediaType = new MediaType().schema(new Schema<>().$ref(API_ERROR_REF));
        for (Map.Entry<String, Example> ejemplo : ejemplos) {
            mediaType.addExamples(ejemplo.getKey(), ejemplo.getValue());
        }
        return new ApiResponse().description(descripcion)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        mediaType));
    }

    private static Map.Entry<String, Example> ejemplo(String nombre, String resumen, int status, ErrorCode code,
            String mensaje, String path, Map<String, String> fieldErrors) {
        Map<String, Object> valor = new LinkedHashMap<>();
        valor.put("timestamp", "2026-10-01T12:00:00Z");
        valor.put("status", status);
        valor.put("error", code.name());
        valor.put("message", mensaje);
        valor.put("path", path);
        valor.put("requestId", REQUEST_ID_EJEMPLO);
        if (!fieldErrors.isEmpty()) { // ApiError omite fieldErrors vacío (@JsonInclude NON_EMPTY)
            valor.put("fieldErrors", new java.util.TreeMap<>(fieldErrors));
        }
        return Map.entry(nombre, new Example().summary(resumen).value(valor));
    }

    private static void agregarHeaderRequestId(ApiResponse response) {
        response.addHeaderObject(RequestContext.REQUEST_ID_HEADER,
                new Header().description("Correlation ID del request (el recibido o uno generado)")
                        .schema(new StringSchema()));
    }
}
