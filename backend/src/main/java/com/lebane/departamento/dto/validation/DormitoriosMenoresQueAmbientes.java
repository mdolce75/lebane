package com.lebane.departamento.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Los dormitorios son un subconjunto de los ambientes (convención argentina: un 3 ambientes tiene hasta 2
 * dormitorios; un monoambiente, 0). El error se informa sobre el campo {@code dormitorios}. La base aplica la misma
 * regla con {@code ck_departamento_dormitorios}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DormitoriosMenoresQueAmbientesValidator.class)
public @interface DormitoriosMenoresQueAmbientes {

    String message() default "debe ser menor que la cantidad de ambientes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
