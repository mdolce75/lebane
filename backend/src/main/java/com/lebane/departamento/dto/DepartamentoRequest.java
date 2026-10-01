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

/**
 * Datos de alta y edición de un departamento (la edición es un reemplazo completo, {@code PUT}).
 *
 * @param estado opcional: en el alta se asume {@link EstadoDepartamento#DISPONIBLE}; en la edición, si se omite se
 *               conserva el estado actual.
 */
@DormitoriosMenoresQueAmbientes
public record DepartamentoRequest(
        @NotBlank @Size(max = 120) String titulo,
        @Size(max = 4000) String descripcion,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio,
        @NotNull Moneda moneda,
        @NotNull @Min(1) @Max(20) Integer ambientes,
        @NotNull @Min(0) @Max(19) Integer dormitorios,
        @NotNull @Min(1) @Max(10) Integer banos,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal superficieM2,
        EstadoDepartamento estado,
        @NotNull @Valid DireccionRequest direccion) {
}
