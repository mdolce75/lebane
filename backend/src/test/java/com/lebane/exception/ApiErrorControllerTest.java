package com.lebane.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.lebane.logging.RequestContext;

import jakarta.servlet.RequestDispatcher;

/**
 * Errores que no pasan por Spring MVC (filtros, firewall de Spring Security, contenedor) y llegan a {@code /error}:
 * mismo esquema {@link ApiError}, con el path original y el requestId, sin detalles de la excepción.
 */
class ApiErrorControllerTest {

    private final ApiErrorController controller = new ApiErrorController();

    @ParameterizedTest
    @CsvSource({
            "400, BAD_REQUEST,          La solicitud no pudo procesarse",
            "401, UNAUTHORIZED,         Se requiere autenticación",
            "403, FORBIDDEN,            Acceso denegado",
            "404, NOT_FOUND,            El recurso solicitado no existe",
            "405, METHOD_NOT_ALLOWED,   Método HTTP no soportado para este recurso",
            "413, PAYLOAD_TOO_LARGE,    La solicitud supera el tamaño máximo permitido",
            "418, BAD_REQUEST,          La solicitud no pudo procesarse",
            "503, SERVICE_UNAVAILABLE,  El servicio no está disponible temporalmente; intentá nuevamente",
            "502, INTERNAL_ERROR,       'Ocurrió un error inesperado; si persiste, informá el requestId'"
    })
    void mapsTheContainerStatusToAStableCodeAndASafeMessage(int status, ErrorCode code, String message) {
        MockHttpServletRequest request = errorRequest(status);

        ResponseEntity<ApiError> response = controller.error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.error()).isEqualTo(code.name());
            assertThat(body.message()).isEqualTo(message);
            assertThat(body.path()).isEqualTo("/api/v1/departamentos;x=1/1");
            assertThat(body.requestId()).isEqualTo("req-error-1");
            assertThat(body.fieldErrors()).isEmpty();
        });
    }

    @Test
    void unhandledExceptionsAre500WithoutTheirMessage() {
        MockHttpServletRequest request = errorRequest(500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("password=s3cr3t"));

        ResponseEntity<ApiError> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).doesNotContain("s3cr3t", "IllegalStateException");
    }

    @Test
    void missingOrUnknownStatusFallsBackTo500AndTheCurrentPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 999);

        ResponseEntity<ApiError> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(response.getBody().path()).isEqualTo("/error");
        assertThat(response.getBody().requestId()).isNull();
    }

    private static MockHttpServletRequest errorRequest(int status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/departamentos;x=1/1");
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, "req-error-1");
        return request;
    }
}
