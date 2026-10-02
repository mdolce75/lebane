package com.lebane.departamento.controller;

import static com.lebane.config.OpenApiConfig.BAD_REQUEST;
import static com.lebane.config.OpenApiConfig.CONFLICT;
import static com.lebane.config.OpenApiConfig.INTERNAL_ERROR;
import static com.lebane.config.OpenApiConfig.NOT_FOUND;
import static com.lebane.config.OpenApiConfig.PAYLOAD_TOO_LARGE;
import static com.lebane.config.OpenApiConfig.SERVICE_UNAVAILABLE;
import static com.lebane.config.OpenApiConfig.UNSUPPORTED_MEDIA_TYPE;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lebane.config.OpenApiConfig;
import com.lebane.departamento.dto.ImagenResponse;
import com.lebane.departamento.service.ImagenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

/**
 * Fotos de un departamento. Una foto por request: el cliente sube en secuencia y puede informar el resultado de cada
 * una (errores parciales) sin que un archivo inválido invalide los demás.
 */
@RestController
@RequestMapping(path = DepartamentoController.BASE_PATH + "/{departamentoId}/imagenes",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_IMAGENES)
public class ImagenController {

    private final ImagenService imagenService;

    public ImagenController(ImagenService imagenService) {
        this.imagenService = imagenService;
    }

    /**
     * {@code multipart/form-data} con el archivo en el campo {@code archivo}. El tipo se determina por el contenido
     * (JPEG, PNG o WebP), no por el nombre ni el Content-Type declarado.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir una foto",
            description = "Una foto por request, en `multipart/form-data` con el archivo en el campo `archivo`. El "
                    + "tipo se detecta por el **contenido** (JPEG, PNG o WebP), no por la extensión ni por el "
                    + "Content-Type declarado: un archivo que no es imagen responde 400. Máximo 5 MB por foto y 5 "
                    + "fotos por departamento (409 `LIMITE_IMAGENES_ALCANZADO`). La primera foto es la principal. Si "
                    + "el storage no está disponible responde 503 `STORAGE_UNAVAILABLE` y no queda nada a medias.")
    @ApiResponse(responseCode = "201", description = "Foto guardada")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "413", ref = PAYLOAD_TOO_LARGE)
    @ApiResponse(responseCode = "415", ref = UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public ImagenResponse subir(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long departamentoId,
            @Parameter(description = "Archivo de imagen (JPEG, PNG o WebP; hasta 5 MB)")
            @RequestPart("archivo") MultipartFile archivo) {
        return imagenService.subir(departamentoId, archivo, archivo.getSize());
    }

    @DeleteMapping("/{imagenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una foto",
            description = "Elimina la foto del departamento. Si el storage no está disponible, la foto se elimina "
                    + "igual (deja de verse en la aplicación) y el archivo queda registrado en los logs como "
                    + "huérfano para limpiarlo después.")
    @ApiResponse(responseCode = "204", description = "Foto eliminada")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    @ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
    public void eliminar(
            @Parameter(description = "ID del departamento", example = "1") @PathVariable @Positive Long departamentoId,
            @Parameter(description = "ID de la foto", example = "19") @PathVariable @Positive Long imagenId) {
        imagenService.eliminar(departamentoId, imagenId);
    }
}
