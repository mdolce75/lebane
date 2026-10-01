package com.lebane.address.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lebane.address.dto.AutocompleteResponse;
import com.lebane.address.service.AddressAutocompleteService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping(path = "/api/v1/direcciones", produces = MediaType.APPLICATION_JSON_VALUE)
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
    public AutocompleteResponse autocompletar(@RequestParam @Size(min = 3, max = 100) String q,
            @RequestParam(required = false) @Min(1) @Max(10) Integer limite) {
        return service.autocompletar(q, limite);
    }
}
