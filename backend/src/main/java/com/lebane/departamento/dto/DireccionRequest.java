package com.lebane.departamento.dto;

import java.math.BigDecimal;

import com.lebane.departamento.dto.validation.CoordenadasCompletas;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@CoordenadasCompletas
@Schema(description = "Dirección. Latitud y longitud van juntas: ambas o ninguna.")
public record DireccionRequest(
        @Schema(description = "Calle", example = "Gorriti")
        @NotBlank @Size(max = 120) String calle,
        @Schema(description = "Altura", example = "4850")
        @NotBlank @Size(max = 10) String numero,
        @Schema(description = "Piso", example = "7")
        @Size(max = 10) String piso,
        @Schema(description = "Unidad o departamento", example = "B")
        @Size(max = 10) String unidad,
        @Schema(description = "Ciudad o localidad", example = "Ciudad Autónoma de Buenos Aires")
        @NotBlank @Size(max = 80) String ciudad,
        @Schema(description = "Provincia", example = "CABA")
        @NotBlank @Size(max = 80) String provincia,
        @Size(max = 10) @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = "solo admite letras, números y espacios")
        @Schema(description = "Código postal (letras, números y espacios)", example = "C1414")
        String codigoPostal,
        @Schema(description = "Latitud (WGS84). Se guarda redondeada a 6 decimales (~10 cm). Requiere longitud", example = "-34.588900")
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = MAX_DECIMALES_COORDENADA) BigDecimal latitud,
        @Schema(description = "Longitud (WGS84). Se guarda redondeada a 6 decimales (~10 cm). Requiere latitud", example = "-58.430100")
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = MAX_DECIMALES_COORDENADA) BigDecimal longitud,
        @Schema(description = "Identificador de la sugerencia del autocompletado de la que proviene la dirección, si se eligió una", example = "georef:0209801005940:4850")
        @Size(max = 200) String placeId) {

    /**
     * Decimales aceptados en las coordenadas. Los proveedores de direcciones (p. ej. Georef) devuelven más de los 6
     * que se guardan: se aceptan y se redondean al persistir ({@link #ESCALA_COORDENADA}), para que elegir una
     * sugerencia del autocompletado nunca impida guardar.
     */
    public static final int MAX_DECIMALES_COORDENADA = 15;
    /** Decimales con los que se guardan las coordenadas (~10 cm de precisión). */
    public static final int ESCALA_COORDENADA = 6;
}
