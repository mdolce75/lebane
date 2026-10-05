package com.lebane.departamento.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import com.lebane.config.ActuatorSecurityProperties;
import com.lebane.config.CorsProperties;
import com.lebane.config.SecurityConfig;
import com.lebane.departamento.TestFixtures;
import com.lebane.departamento.dto.ConsultaCreatedResponse;
import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DireccionResponse;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;
import com.lebane.departamento.service.ConsultaService;
import com.lebane.departamento.service.DepartamentoListadoService;
import com.lebane.departamento.service.DepartamentoService;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.ErrorCode;
import com.lebane.exception.PreconditionFailedException;
import com.lebane.exception.ResourceNotFoundException;

/**
 * Contrato HTTP de la API de departamentos y esquema de errores ({@code ApiError}), con los servicios simulados.
 */
@WebMvcTest(DepartamentoController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({ActuatorSecurityProperties.class, CorsProperties.class})
class DepartamentoControllerTest {

    private static final String BASE = "/api/v1/departamentos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartamentoService departamentoService;
    @MockitoBean
    private DepartamentoListadoService listadoService;
    @MockitoBean
    private ConsultaService consultaService;

    // ---------- Casos exitosos ----------

    @Test
    void crearReturns201WithRelativeLocationAndEtag() throws Exception {
        when(departamentoService.crear(any())).thenReturn(detalle(15L, 0));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(TestFixtures.departamentoJson())
                        .header("X-Request-Id", "req-create-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/15"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("X-Request-Id", "req-create-1"))
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.codigo").value("DEP-ABCDEFGH"))
                .andExpect(jsonPath("$.direccion.ciudad").value("CABA"));
    }

    @Test
    void obtenerReturnsDetailWithEtag() throws Exception {
        when(departamentoService.obtenerDetalle(15L)).thenReturn(detalle(15L, 4));

        mockMvc.perform(get(BASE + "/15"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.cantidadConsultas").value(2))
                .andExpect(jsonPath("$.imagenes").isArray());
    }

    @Test
    void actualizarForwardsIfMatchVersions() throws Exception {
        when(departamentoService.actualizar(eq(15L), any(), eq(Set.of(4L)))).thenReturn(detalle(15L, 5));

        mockMvc.perform(put(BASE + "/15").contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"4\"").content(TestFixtures.departamentoJson()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""));
    }

    @Test
    void darDeBajaReturns204AndForwardsIfMatch() throws Exception {
        mockMvc.perform(delete(BASE + "/15").header("If-Match", "\"4\""))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(departamentoService).darDeBaja(15L, Set.of(4L));
    }

    @Test
    void darDeBajaDeUnoInexistenteEs404() throws Exception {
        doThrow(new ResourceNotFoundException("departamento"))
                .when(departamentoService).darDeBaja(eq(99L), any());

        mockMvc.perform(delete(BASE + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void reactivarReturns200WithEtagAndForwardsIfMatch() throws Exception {
        when(departamentoService.reactivar(15L, Set.of(6L))).thenReturn(detalle(15L, 7));

        mockMvc.perform(post(BASE + "/15/reactivacion").header("If-Match", "\"6\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.id").value(15));
    }

    @Test
    void reactivarUnoPublicadoEs409() throws Exception {
        when(departamentoService.reactivar(eq(15L), any())).thenThrow(new BusinessRuleException(
                ErrorCode.DEPARTAMENTO_NO_DADO_DE_BAJA, "El departamento no está dado de baja"));

        mockMvc.perform(post(BASE + "/15/reactivacion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DEPARTAMENTO_NO_DADO_DE_BAJA"));
    }

    @Test
    void crearConsultaReturns201WithoutPersonalData() throws Exception {
        when(consultaService.crear(eq(15L), any()))
                .thenReturn(new ConsultaCreatedResponse(9L, 15L, Instant.parse("2026-10-01T12:00:00Z")));

        mockMvc.perform(post(BASE + "/15/consultas").contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.consultaJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.departamentoId").value(15))
                .andExpect(content().string(not(containsString("example.com"))));
    }

    @Test
    void listarReturnsSpringDataPageShape() throws Exception {
        DepartamentoListItemResponse item = new DepartamentoListItemResponse(15L, "DEP-ABCDEFGH", "3 ambientes",
                new BigDecimal("185000.00"), Moneda.USD, 3, 2, 1, new BigDecimal("72.50"),
                EstadoDepartamento.DISPONIBLE, "CABA", "CABA", "http://localhost:9000/b/a.jpg", 2, 4,
                Instant.parse("2026-10-01T12:00:00Z"), null);
        when(listadoService.listar(any())).thenReturn(
                new PagedModel<>(new PageImpl<>(List.of(item), PageRequest.of(1, 1), 3)));

        mockMvc.perform(get(BASE).param("estado", "DISPONIBLE,RESERVADO").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(15))
                .andExpect(jsonPath("$.content[0].imagenPrincipalUrl").value("http://localhost:9000/b/a.jpg"))
                .andExpect(jsonPath("$.content[0].cantidadImagenes").value(2))
                .andExpect(jsonPath("$.content[0].cantidadConsultas").value(4))
                .andExpect(jsonPath("$.content[0].descripcion").doesNotExist())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void listarBindsAndNormalizesQueryParams() throws Exception {
        when(listadoService.listar(any())).thenReturn(new PagedModel<>(new PageImpl<>(List.of())));

        mockMvc.perform(get(BASE).param("q", "  balcón ").param("ciudad", "Rosario")
                        .param("estado", "DISPONIBLE").param("estado", "RESERVADO")
                        .param("moneda", "USD").param("precioMin", "100000").param("precioMax", "200000")
                        .param("conImagenes", "true").param("sort", "precio,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<DepartamentoListadoParams> params = ArgumentCaptor.forClass(DepartamentoListadoParams.class);
        verify(listadoService).listar(params.capture());
        assertThat(params.getValue().q()).isEqualTo("balcón");
        assertThat(params.getValue().estado())
                .containsExactly(EstadoDepartamento.DISPONIBLE, EstadoDepartamento.RESERVADO);
        assertThat(params.getValue().conImagenes()).isTrue();
        assertThat(params.getValue().sort()).isEqualTo("precio,desc");
    }

    /**
     * Errores de conversión (enum o número inválido): el record no llega a construirse, por lo que se informan solo
     * esos campos, con un mensaje genérico (nunca el texto técnico de Spring con nombres de clases).
     */
    @Test
    void listarRejectsUnconvertibleParamsWithoutTechnicalDetails() throws Exception {
        mockMvc.perform(get(BASE).param("estado", "ALQUILADO").param("precioMin", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.estado").value("tiene un formato o valor inválido"))
                .andExpect(jsonPath("$.fieldErrors.precioMin").value("tiene un formato o valor inválido"))
                .andExpect(content().string(not(containsString("java."))))
                .andExpect(content().string(not(containsString("Failed to convert"))));
        verifyNoInteractions(listadoService);
    }

    @Test
    void listarRejectsOutOfRangeParams() throws Exception {
        mockMvc.perform(get(BASE).param("size", "500").param("sort", "titulo").param("q", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists())
                .andExpect(jsonPath("$.fieldErrors.sort").exists())
                .andExpect(jsonPath("$.fieldErrors.q").exists());
        verifyNoInteractions(listadoService);
    }

    @Test
    void listarRequiresMonedaForPriceFilter() throws Exception {
        mockMvc.perform(get(BASE).param("precioMax", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.moneda").value("es obligatoria para filtrar por precio"));
    }

    // ---------- Validación ----------

    @Test
    void invalidBodyReturnsValidationErrorWithFieldErrors() throws Exception {
        String invalid = """
                {"titulo": "", "precio": -5, "moneda": "USD", "ambientes": 2, "dormitorios": 2, "banos": 1,
                 "superficieM2": 40, "direccion": {"calle": "Gorriti", "numero": "1", "ciudad": "",
                 "provincia": "CABA"}}
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(invalid)
                        .header("X-Request-Id", "req-invalid-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La solicitud contiene datos inválidos"))
                .andExpect(jsonPath("$.path").value(BASE))
                .andExpect(jsonPath("$.requestId").value("req-invalid-1"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.titulo").exists())
                .andExpect(jsonPath("$.fieldErrors.precio").value("debe ser mayor que 0"))
                .andExpect(jsonPath("$.fieldErrors.dormitorios")
                        .value("debe ser menor que la cantidad de ambientes"))
                .andExpect(jsonPath("$.fieldErrors['direccion.ciudad']").exists());
        verifyNoInteractions(departamentoService);
    }

    @Test
    void unknownEnumValueIsReportedOnItsField() throws Exception {
        String body = TestFixtures.departamentoJson().replace("\"USD\"", "\"EUR\"");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.moneda").value("valor inválido; valores permitidos: ARS, USD"));
    }

    @Test
    void wrongTypeIsReportedOnNestedField() throws Exception {
        String body = TestFixtures.departamentoJson().replace("\"latitud\": -34.5889", "\"latitud\": \"norte\"");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['direccion.latitud']").exists());
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutParserDetails() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"titulo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es JSON válido"))
                .andExpect(content().string(not(containsString("Jackson"))))
                .andExpect(content().string(not(containsString("line:"))));
    }

    @Test
    void nonNumericIdIsValidationError() throws Exception {
        mockMvc.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("tiene un formato inválido"));
    }

    @Test
    void nonPositiveIdIsValidationError() throws Exception {
        mockMvc.perform(get(BASE + "/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.id").exists());
    }

    /** El PUT tiene {@code @Positive} en el id: los errores del body llegan por validación de método. */
    @Test
    void invalidUpdateBodyIsReportedPerField() throws Exception {
        String body = TestFixtures.departamentoJson().replace("\"dormitorios\": 2", "\"dormitorios\": 3")
                .replace("\"ciudad\": \"Ciudad Autónoma de Buenos Aires\"", "\"ciudad\": \"\"");

        mockMvc.perform(put(BASE + "/15").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.dormitorios")
                        .value("debe ser menor que la cantidad de ambientes"))
                .andExpect(jsonPath("$.fieldErrors['direccion.ciudad']").exists())
                .andExpect(jsonPath("$.fieldErrors.request").doesNotExist());
        verifyNoInteractions(departamentoService);
    }

    @Test
    void invalidConsultaIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post(BASE + "/15/consultas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"no-mail\",\"mensaje\":\"hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.mensaje").exists());
        verifyNoInteractions(consultaService);
    }

    // ---------- Errores de dominio y persistencia ----------

    @Test
    void missingDepartamentoReturns404() throws Exception {
        when(departamentoService.obtenerDetalle(99L)).thenThrow(new ResourceNotFoundException("departamento"));

        mockMvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró el departamento solicitado"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void staleIfMatchReturns412() throws Exception {
        when(departamentoService.actualizar(eq(15L), any(), any())).thenThrow(new PreconditionFailedException());

        mockMvc.perform(put(BASE + "/15").contentType(MediaType.APPLICATION_JSON).header("If-Match", "\"1\"")
                        .content(TestFixtures.departamentoJson()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error").value("PRECONDITION_FAILED"));
    }

    @Test
    void concurrentModificationReturns409() throws Exception {
        when(departamentoService.actualizar(eq(15L), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Departamento.class, 15L));

        mockMvc.perform(put(BASE + "/15").contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.departamentoJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void soldDepartamentoRejectsConsultaWith409() throws Exception {
        when(consultaService.crear(eq(15L), any())).thenThrow(new BusinessRuleException(
                ErrorCode.DEPARTAMENTO_NO_DISPONIBLE, "El departamento ya no está disponible"));

        mockMvc.perform(post(BASE + "/15/consultas").contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.consultaJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DEPARTAMENTO_NO_DISPONIBLE"));
    }

    @Test
    void dataIntegrityViolationDoesNotExposeSql() throws Exception {
        when(departamentoService.crear(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint "
                        + "\"uk_departamento_codigo\"] [insert into departamento ...]"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(TestFixtures.departamentoJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(content().string(not(containsString("insert"))))
                .andExpect(content().string(not(containsString("uk_departamento_codigo"))));
    }

    @Test
    void databaseUnavailableReturns503() throws Exception {
        when(departamentoService.obtenerDetalle(15L))
                .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager: jdbc://postgres"));

        mockMvc.perform(get(BASE + "/15"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("postgres"))));
    }

    @Test
    void unexpectedErrorReturnsGeneric500WithoutInternals() throws Exception {
        when(departamentoService.obtenerDetalle(15L))
                .thenThrow(new IllegalStateException("NullPointer in com.lebane.secret.Internal"));

        mockMvc.perform(get(BASE + "/15").header("X-Request-Id", "req-boom-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value("req-boom-1"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("com.lebane"))));
    }

    // ---------- Protocolo HTTP ----------

    @Test
    void unsupportedMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(patch(BASE + "/15"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownRouteReturnsApiError404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe").header("X-Request-Id", "req-404-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("req-404-1"));
    }

    private static DepartamentoDetailResponse detalle(Long id, long version) {
        return new DepartamentoDetailResponse(id, "DEP-ABCDEFGH", "3 ambientes en Palermo", null,
                new BigDecimal("185000.00"), Moneda.USD, 3, 2, 1, new BigDecimal("72.50"),
                EstadoDepartamento.DISPONIBLE,
                new DireccionResponse("Gorriti", "4850", "7", "B", "CABA", "CABA", "C1414", null, null, null),
                List.of(), 2, version, Instant.parse("2026-10-01T12:00:00Z"), Instant.parse("2026-10-01T12:00:00Z"), null);
    }
}
