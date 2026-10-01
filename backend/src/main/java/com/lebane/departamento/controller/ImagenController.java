package com.lebane.departamento.controller;

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

import com.lebane.departamento.dto.ImagenResponse;
import com.lebane.departamento.service.ImagenService;

import jakarta.validation.constraints.Positive;

/**
 * Fotos de un departamento. Una foto por request: el cliente sube en secuencia y puede informar el resultado de cada
 * una (errores parciales) sin que un archivo inválido invalide los demás.
 */
@RestController
@RequestMapping(path = DepartamentoController.BASE_PATH + "/{departamentoId}/imagenes",
        produces = MediaType.APPLICATION_JSON_VALUE)
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
    public ImagenResponse subir(@PathVariable @Positive Long departamentoId,
            @RequestPart("archivo") MultipartFile archivo) {
        return imagenService.subir(departamentoId, archivo, archivo.getSize());
    }

    @DeleteMapping("/{imagenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable @Positive Long departamentoId, @PathVariable @Positive Long imagenId) {
        imagenService.eliminar(departamentoId, imagenId);
    }
}
