package com.lebane.departamento.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Reglas entre parámetros del listado: el precio solo se filtra con moneda (ARS y USD no son comparables), los
 * rangos tienen mínimo ≤ máximo y la página no supera la ventana máxima de resultados. Cada error se informa sobre
 * el parámetro a corregir.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ListadoParamsValidosValidator.class)
public @interface ListadoParamsValidos {

    String message() default "parámetros de listado inconsistentes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
