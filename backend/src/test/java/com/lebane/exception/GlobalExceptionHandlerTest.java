package com.lebane.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.fasterxml.jackson.core.JsonParseException;
import com.lebane.logging.RequestContext;

/**
 * Casos del manejador global que no se alcanzan con los endpoints actuales (o solo con requests difíciles de
 * provocar con MockMvc): cada excepción produce el status y el código esperados, con el requestId del request y
 * <b>sin</b> mensajes técnicos (clases, SQL, constraints, valores de la excepción original).
 */
class GlobalExceptionHandlerTest {

    private static final String SECRETO = "SELECT * FROM departamento -- password=s3cr3t";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = request();

    @Test
    void nonMultipartUploadIs400WithAnActionableMessage() {
        ResponseEntity<ApiError> response = handler.handleMultipart(new MultipartException(SECRETO), request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("multipart/form-data", "'archivo'");
    }

    @Test
    void missingRequiredHeaderNamesTheHeaderOnly() throws Exception {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

        ResponseEntity<ApiError> response = handler.handleMissingHeader(
                new MissingRequestHeaderException("If-Match", parameter), request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Falta el header obligatorio If-Match");
    }

    @Test
    void unreadableBodyDistinguishesInvalidJsonFromMissingBody() {
        HttpMessageNotReadableException invalido = new HttpMessageNotReadableException(SECRETO,
                new JsonParseException(null, SECRETO), new MockHttpInputMessage(new byte[0]));
        HttpMessageNotReadableException ausente = new HttpMessageNotReadableException(SECRETO,
                new IOException(SECRETO), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiError> invalidoResponse = handler.handleUnreadable(invalido, request);
        ResponseEntity<ApiError> ausenteResponse = handler.handleUnreadable(ausente, request);

        assertError(invalidoResponse, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
        assertThat(invalidoResponse.getBody().message()).isEqualTo("El cuerpo de la solicitud no es JSON válido");
        assertError(ausenteResponse, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
        assertThat(ausenteResponse.getBody().message())
                .isEqualTo("El cuerpo de la solicitud es obligatorio y debe ser JSON válido");
    }

    @Test
    void methodNotAllowedWithoutKnownMethodsHasNoAllowHeader() {
        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"), request);

        assertError(response, HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().containsKey(HttpHeaders.ALLOW)).isFalse();
    }

    @Test
    void notAcceptableHasNoBodyBecauseTheClientDoesNotAcceptJson() {
        ResponseEntity<Void> response = handler.handleNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void multipartOverTheContainerLimitIs413() {
        ResponseEntity<ApiError> response = handler.handleMaxUpload(new MaxUploadSizeExceededException(5_242_880), request);

        assertError(response, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void springExceptionsKeepTheirClientStatusWithAGenericMessage() {
        ResponseEntity<ApiError> conflicto = handler.handleSpringErrorResponse(
                new ErrorResponseException(HttpStatus.CONFLICT, new IllegalStateException(SECRETO)), request);
        ResponseEntity<ApiError> binding = handler.handleSpringErrorResponse(
                new ServletRequestBindingException(SECRETO), request);

        assertError(conflicto, HttpStatus.CONFLICT, ErrorCode.CONFLICT);
        assertThat(conflicto.getBody().message()).isEqualTo("La solicitud no pudo procesarse");
        assertError(binding, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
    }

    @Test
    void springExceptionsWith5xxAreTreatedAsUnexpected() {
        ResponseEntity<ApiError> response = handler.handleSpringErrorResponse(
                new ErrorResponseException(HttpStatus.BAD_GATEWAY), request);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void constraintViolationsAre409WithoutSqlNorConstraintName() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(SECRETO,
                new ConstraintViolationException(SECRETO, new SQLException(SECRETO), "uk_departamento_codigo"));

        ResponseEntity<ApiError> response = handler.handleDataIntegrity(ex, request);

        assertError(response, HttpStatus.CONFLICT, ErrorCode.CONFLICT);
        assertThat(response.getBody().toString()).doesNotContain("uk_departamento_codigo");
    }

    @Test
    void databaseUnavailableIs503() {
        ResponseEntity<ApiError> response = handler.handleDatabaseUnavailable(
                new CannotCreateTransactionException(SECRETO, new SQLException(SECRETO)), request);

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void unexpectedErrorsAre500WithAGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException(SECRETO), request);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Ocurrió un error inesperado; si persiste, informá el requestId");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/departamentos/7/imagenes");
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, "req-handler-1");
        return request;
    }

    private static void assertError(ResponseEntity<ApiError> response, HttpStatus status, ErrorCode code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.error()).isEqualTo(code.name());
        assertThat(body.path()).isEqualTo("/api/departamentos/7/imagenes");
        assertThat(body.requestId()).isEqualTo("req-handler-1");
        assertThat(body.message()).doesNotContain("SELECT", "password", "s3cr3t", "Exception", "com.");
    }
}
