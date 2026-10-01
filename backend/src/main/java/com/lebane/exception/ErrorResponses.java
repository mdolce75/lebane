package com.lebane.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.lebane.logging.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

/** Construcción homogénea de respuestas {@link ApiError}, compartida por el handler global y el /error. */
final class ErrorResponses {

    private ErrorResponses() {
    }

    static ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message, String path,
            HttpServletRequest request, Map<String, String> fieldErrors) {
        ApiError body = ApiError.of(status.value(), code, message, path, requestId(request), fieldErrors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    static String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return attribute instanceof String id ? id : RequestContext.currentRequestId().orElse(null);
    }

    /** Código estable por defecto para un status HTTP sin un código más específico. */
    static ErrorCode codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> ErrorCode.NOT_ACCEPTABLE;
            case CONFLICT -> ErrorCode.CONFLICT;
            case PRECONDITION_FAILED -> ErrorCode.PRECONDITION_FAILED;
            case PAYLOAD_TOO_LARGE -> ErrorCode.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    /** Mensaje genérico y seguro por status (sin detalles internos). */
    static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe";
            case METHOD_NOT_ALLOWED -> "Método HTTP no soportado para este recurso";
            case UNAUTHORIZED -> "Se requiere autenticación";
            case FORBIDDEN -> "Acceso denegado";
            case PAYLOAD_TOO_LARGE -> "La solicitud supera el tamaño máximo permitido";
            case SERVICE_UNAVAILABLE -> "El servicio no está disponible temporalmente; intentá nuevamente";
            default -> status.is4xxClientError()
                    ? "La solicitud no pudo procesarse"
                    : "Ocurrió un error inesperado; si persiste, informá el requestId";
        };
    }
}
