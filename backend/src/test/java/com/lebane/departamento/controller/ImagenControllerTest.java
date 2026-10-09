package com.lebane.departamento.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lebane.config.ActuatorSecurityProperties;
import com.lebane.config.CorsProperties;
import com.lebane.config.SecurityConfig;
import com.lebane.departamento.dto.ImagenResponse;
import com.lebane.departamento.service.ImagenService;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.DependencyUnavailableException;
import com.lebane.exception.ErrorCode;
import com.lebane.exception.InvalidRequestException;
import com.lebane.exception.PayloadTooLargeException;
import com.lebane.storage.TestImages;

@WebMvcTest(ImagenController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({ActuatorSecurityProperties.class, CorsProperties.class})
class ImagenControllerTest {

    private static final String BASE = "/api/departamentos/7/imagenes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImagenService imagenService;

    @Test
    void uploadReturns201WithTheImage() throws Exception {
        when(imagenService.subir(eq(7L), any(), eq(1024L)))
                .thenReturn(new ImagenResponse(3L, "http://cdn/b/departamentos/7/x.png", "image/png", 1024, 0));

        mockMvc.perform(multipart(BASE).file(archivo(TestImages.pngLike(1024))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.url").value("http://cdn/b/departamentos/7/x.png"))
                .andExpect(jsonPath("$.posicion").value(0));
    }

    @Test
    void missingFilePartIsAFieldError() throws Exception {
        mockMvc.perform(multipart(BASE).file(new MockMultipartFile("otro", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.archivo").value("es obligatorio"));
        verifyNoInteractions(imagenService);
    }

    @Test
    void nonMultipartRequestIsRejected() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void serviceErrorsKeepTheApiErrorContract() throws Exception {
        when(imagenService.subir(eq(7L), any(), anyLong()))
                .thenThrow(new InvalidRequestException("archivo", "debe ser una imagen JPEG, PNG o WebP"))
                .thenThrow(new PayloadTooLargeException("La imagen supera el tamaño máximo de 5 MB"))
                .thenThrow(new BusinessRuleException(ErrorCode.LIMITE_IMAGENES_ALCANZADO, "máximo de 5 fotos"))
                .thenThrow(new DependencyUnavailableException(ErrorCode.STORAGE_UNAVAILABLE,
                        "El servicio de imágenes no está disponible", new RuntimeException("http://minio:9000")));

        mockMvc.perform(multipart(BASE).file(archivo(new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.archivo").value("debe ser una imagen JPEG, PNG o WebP"));
        mockMvc.perform(multipart(BASE).file(archivo(new byte[] {1, 2, 3})))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"));
        mockMvc.perform(multipart(BASE).file(archivo(new byte[] {1, 2, 3})))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LIMITE_IMAGENES_ALCANZADO"));
        mockMvc.perform(multipart(BASE).file(archivo(new byte[] {1, 2, 3})))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("STORAGE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("minio"))));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/3")).andExpect(status().isNoContent());

        verify(imagenService).eliminar(7L, 3L);
    }

    @Test
    void deleteOfUnknownImageIs404() throws Exception {
        doThrow(new com.lebane.exception.ResourceNotFoundException("imagen")).when(imagenService).eliminar(7L, 9L);

        mockMvc.perform(delete(BASE + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el imagen solicitado"));
    }

    private static MockMultipartFile archivo(byte[] content) {
        return new MockMultipartFile("archivo", "foto.png", "image/png", content);
    }
}
