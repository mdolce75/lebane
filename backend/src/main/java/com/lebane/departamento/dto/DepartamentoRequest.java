package com.lebane.departamento.dto;

import java.math.BigDecimal;

import com.lebane.departamento.dto.validation.DormitoriosMenoresQueAmbientes;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos de alta y edición de un departamento (la edición es un reemplazo completo, {@code PUT}).
 *
 * @param estado opcional: en el alta se asume {@link EstadoDepartamento#DISPONIBLE}; en la edición, si se omite se
 *               conserva el estado actual.
 */
@DormitoriosMenoresQueAmbientes
@Schema(description = "Datos de un departamento para el alta o la edición (reemplazo completo). Regla adicional: dormitorios debe ser menor que ambientes.")
public record DepartamentoRequest(
        @Schema(description = "Título de la publicación", example = "Luminoso 3 ambientes con balcón en Palermo")
        @NotBlank @Size(max = 120) String titulo,
        @Schema(description = "Descripción libre", example = "Frente, piso alto, cocina integrada.")
        @Size(max = 4000) String descripcion,
        @Schema(description = "Precio de venta, en la moneda indicada (hasta 2 decimales)", example = "185000")
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio,
        @Schema(description = "Moneda del precio", example = "USD")
        @NotNull Moneda moneda,
        @Schema(description = "Cantidad de ambientes", example = "3")
        @NotNull @Min(1) @Max(20) Integer ambientes,
        @Schema(description = "Cantidad de dormitorios: menor que ambientes (monoambiente: 0)", example = "2")
        @NotNull @Min(0) @Max(19) Integer dormitorios,
        @Schema(description = "Cantidad de baños", example = "1")
        @NotNull @Min(1) @Max(10) Integer banos,
        @Schema(description = "Superficie total en m² (hasta 2 decimales)", example = "72.5")
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal superficieM2,
        @Schema(description = "Estado comercial. En el alta, si se omite, DISPONIBLE; en la edición, si se omite, se conserva el actual", example = "DISPONIBLE")
        EstadoDepartamento estado,
        @Schema(description = "Dirección del departamento")
        @NotNull @Valid DireccionRequest direccion) {
}
