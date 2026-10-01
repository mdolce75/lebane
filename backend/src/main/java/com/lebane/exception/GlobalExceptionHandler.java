package com.lebane.exception;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traducción única de excepciones a {@link ApiError}.
 *
 * <p>Política de logging: cada excepción se registra una sola vez, aquí.
 * <ul>
 *   <li>4xx esperados (validación, no encontrado, conflictos): DEBUG, sin stack trace. No son incidentes.</li>
 *   <li>503 por dependencia caída (base de datos): WARN, sin stack trace.</li>
 *   <li>500 inesperado: ERROR con stack trace (el cliente solo recibe un mensaje genérico y el requestId).</li>
 * </ul>
 * Nunca se devuelven al cliente mensajes de excepciones técnicas, SQL ni nombres de constraints.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String VALIDATION_MESSAGE = "La solicitud contiene datos inválidos";

    // ---------- Errores de la aplicación ----------

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        logClientError(ex.getErrorCode(), ex.getStatus(), ex);
        return respond(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request, Map.of());
    }

    // ---------- Validación y formato de entrada ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return validationError(request, fieldErrors);
    }

    /** Restricciones sobre parámetros de controllers ({@code @PathVariable}, {@code @RequestParam}). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> fieldErrors.putIfAbsent(
                    name != null ? name : "parametro", error.getDefaultMessage()));
        });
        return validationError(request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return validationError(request, Map.of(ex.getName(), "tiene un formato inválido"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        return validationError(request, Map.of(ex.getParameterName(), "es obligatorio"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        logClientError(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, ex);
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                "Falta el header obligatorio " + ex.getHeaderName(), request, Map.of());
    }

    /**
     * JSON mal formado o con tipos incorrectos. Si se puede identificar el campo (p. ej. un enum con un valor
     * desconocido), se informa en {@code fieldErrors}; nunca se devuelve el mensaje interno de Jackson.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return validationError(request, Map.of(fieldPath(mismatch), describe(mismatch)));
        }
        String message = ex.getCause() instanceof JsonProcessingException
                ? "El cuerpo de la solicitud no es JSON válido"
                : "El cuerpo de la solicitud es obligatorio y debe ser JSON válido";
        logClientError(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, ex);
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message, request, Map.of());
    }

    // ---------- Protocolo HTTP ----------

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        logClientError(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, ex);
        return respond(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ErrorResponses.messageFor(HttpStatus.NOT_FOUND),
                request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        logClientError(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED, ex);
        ResponseEntity<ApiError> response = respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                ErrorResponses.messageFor(HttpStatus.METHOD_NOT_ALLOWED), request, Map.of());
        if (ex.getSupportedHttpMethods() == null) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new))
                .body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        logClientError(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex);
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de contenido no soportado", request, Map.of());
    }

    /** El cliente no acepta JSON: no es posible devolver un cuerpo {@link ApiError}. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        logClientError(ErrorCode.NOT_ACCEPTABLE, HttpStatus.NOT_ACCEPTABLE, ex);
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        logClientError(ErrorCode.PAYLOAD_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, ex);
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE,
                ErrorResponses.messageFor(HttpStatus.PAYLOAD_TOO_LARGE), request, Map.of());
    }

    /** Resto de excepciones de Spring MVC que ya declaran su status HTTP ({@link ErrorResponse}). */
    @ExceptionHandler({ErrorResponseException.class, ServletRequestBindingException.class,
            NoHandlerFoundException.class})
    ResponseEntity<ApiError> handleSpringErrorResponse(Exception ex, HttpServletRequest request) {
        HttpStatus status = ex instanceof ErrorResponse errorResponse
                ? HttpStatus.resolve(errorResponse.getStatusCode().value())
                : null;
        if (status == null || status.is5xxServerError()) {
            return handleUnexpected(ex, request);
        }
        ErrorCode code = ErrorResponses.codeFor(status);
        logClientError(code, status, ex);
        return respond(status, code, ErrorResponses.messageFor(status), request, Map.of());
    }

    // ---------- Persistencia ----------

    /** Otra transacción modificó el recurso entre la lectura y la escritura. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        logClientError(ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT, ex);
        return respond(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "El recurso fue modificado por otra operación; recargalo y volvé a intentar", request, Map.of());
    }

    /**
     * Violación de una restricción de la base (unicidad, CHECK, FK). La validación de entrada debería evitarlas; si
     * ocurren (p. ej. carreras entre requests) se responde 409 sin exponer SQL. El nombre de la constraint se
     * registra en el log para diagnóstico.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String constraint = ex.getCause() instanceof ConstraintViolationException cve ? cve.getConstraintName() : null;
        log.warn("Data integrity violation", kv("errorCode", ErrorCode.CONFLICT), kv("status", 409),
                kv("constraint", constraint));
        return respond(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "La operación entra en conflicto con el estado actual de los datos", request, Map.of());
    }

    /** Base de datos no disponible: dependencia degradada, no un bug. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            QueryTimeoutException.class})
    ResponseEntity<ApiError> handleDatabaseUnavailable(Exception ex, HttpServletRequest request) {
        log.warn("Database unavailable", kv("errorCode", ErrorCode.SERVICE_UNAVAILABLE), kv("status", 503),
                kv("cause", ex.getClass().getSimpleName()));
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE,
                ErrorResponses.messageFor(HttpStatus.SERVICE_UNAVAILABLE), request, Map.of());
    }

    // ---------- Inesperado ----------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", kv("errorCode", ErrorCode.INTERNAL_ERROR), kv("status", 500), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                ErrorResponses.messageFor(HttpStatus.INTERNAL_SERVER_ERROR), request, Map.of());
    }

    // ---------- Soporte ----------

    private ResponseEntity<ApiError> validationError(HttpServletRequest request, Map<String, String> fieldErrors) {
        if (log.isDebugEnabled()) {
            log.debug("Validation failed", kv("errorCode", ErrorCode.VALIDATION_ERROR), kv("status", 400),
                    kv("fields", fieldErrors.keySet()));
        }
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, VALIDATION_MESSAGE, request, fieldErrors);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, ErrorCode code, String message,
            HttpServletRequest request, Map<String, String> fieldErrors) {
        return ErrorResponses.build(status, code, message, request.getRequestURI(), request, fieldErrors);
    }

    private static void logClientError(ErrorCode code, HttpStatus status, Exception ex) {
        if (log.isDebugEnabled()) {
            log.debug("Client error", kv("errorCode", code), kv("status", status.value()),
                    kv("exception", ex.getClass().getSimpleName()));
        }
    }

    private static String fieldPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : ex.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String describe(MismatchedInputException ex) {
        Class<?> target = ex.getTargetType();
        if (ex instanceof InvalidFormatException && target != null && target.isEnum()) {
            String allowed = Arrays.stream(target.getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "valor inválido; valores permitidos: " + allowed;
        }
        return "tiene un formato o tipo inválido";
    }
}
