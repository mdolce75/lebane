package com.lebane.address.controller;

import static com.lebane.config.OpenApiConfig.BAD_REQUEST;
import static com.lebane.config.OpenApiConfig.INTERNAL_ERROR;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lebane.address.dto.AutocompleteResponse;
import com.lebane.address.service.AddressAutocompleteService;
import com.lebane.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping(path = "/api/direcciones", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_DIRECCIONES)
public class DireccionController {

    private final AddressAutocompleteService service;

    public DireccionController(AddressAutocompleteService service) {
        this.service = service;
    }

    /**
     * Sugerencias para una dirección parcial (p. ej. {@code q=Gorriti 4850}). Siempre 200 si los parámetros son
     * válidos: si el proveedor no está disponible, {@code degradado: true} y sin sugerencias.
     */
    @GetMapping("/autocompletar")
    @Operation(summary = "Autocompletar una dirección",
            description = "Sugerencias para una dirección parcial, con calle, altura, ciudad, provincia y "
                    + "coordenadas. Siempre responde 200 si los parámetros son válidos: si el proveedor externo "
                    + "falla, tarda o tiene el circuito abierto, devuelve `degradado: true`, sin sugerencias y con "
                    + "un mensaje para cargar la dirección a mano (nunca datos inventados).")
    @ApiResponse(responseCode = "200", description = "Sugerencias, o respuesta degradada si el proveedor no está "
            + "disponible")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "500", ref = INTERNAL_ERROR)
    public AutocompleteResponse autocompletar(
            @Parameter(description = "Dirección parcial (3 a 100 caracteres)", example = "Gorriti 4850")
            @RequestParam @Size(min = 3, max = 100) String q,
            @Parameter(description = "Máximo de sugerencias (1 a 10; por defecto, el configurado en el backend)",
                    example = "5")
            @RequestParam(required = false) @Min(1) @Max(10) Integer limite) {
        return service.autocompletar(q, limite);
    }
}
