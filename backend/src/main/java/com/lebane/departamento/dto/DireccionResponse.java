package com.lebane.departamento.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dirección del departamento.")
public record DireccionResponse(
        @Schema(description = "Calle", example = "Gorriti", requiredMode = Schema.RequiredMode.REQUIRED)
        String calle,
        @Schema(description = "Altura", example = "4850", requiredMode = Schema.RequiredMode.REQUIRED)
        String numero,
        @Schema(description = "Piso", example = "7")
        String piso,
        @Schema(description = "Unidad o departamento", example = "B")
        String unidad,
        @Schema(description = "Ciudad o localidad", example = "Ciudad Autónoma de Buenos Aires", requiredMode = Schema.RequiredMode.REQUIRED)
        String ciudad,
        @Schema(description = "Provincia", example = "CABA", requiredMode = Schema.RequiredMode.REQUIRED)
        String provincia,
        @Schema(description = "Código postal", example = "C1414")
        String codigoPostal,
        @Schema(description = "Latitud (WGS84)", example = "-34.588900")
        BigDecimal latitud,
        @Schema(description = "Longitud (WGS84)", example = "-58.430100")
        BigDecimal longitud,
        @Schema(description = "Identificador de la sugerencia de autocompletado de la que proviene", example = "georef:0209801005940:4850")
        String placeId) {
}
