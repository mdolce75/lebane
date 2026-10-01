package com.lebane.exception;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Reemplaza al {@code BasicErrorController} de Spring Boot: los errores que no pasan por Spring MVC (excepciones en
 * filtros, errores del contenedor) también responden con {@link ApiError}, con requestId y sin detalles internos.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        ErrorCode code = ErrorResponses.codeFor(status);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (status.is5xxServerError() && exception instanceof Throwable throwable) {
            log.error("Unhandled error outside MVC", kv("errorCode", code), kv("status", status.value()), throwable);
        }
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalPath instanceof String value ? value : request.getRequestURI();
        return ErrorResponses.build(status, code, ErrorResponses.messageFor(status), path, request, Map.of());
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
