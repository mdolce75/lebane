package com.lebane.departamento.dto;

import java.math.BigDecimal;

import com.lebane.departamento.dto.validation.CoordenadasCompletas;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@CoordenadasCompletas
public record DireccionRequest(
        @NotBlank @Size(max = 120) String calle,
        @NotBlank @Size(max = 10) String numero,
        @Size(max = 10) String piso,
        @Size(max = 10) String unidad,
        @NotBlank @Size(max = 80) String ciudad,
        @NotBlank @Size(max = 80) String provincia,
        @Size(max = 10) @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = "solo admite letras, números y espacios")
        String codigoPostal,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitud,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitud,
        @Size(max = 200) String placeId) {
}
