package com.lebane.departamento.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Latitud y longitud se informan juntas o ninguna. El error se informa sobre el campo faltante. La base aplica la
 * misma regla con {@code ck_departamento_coordenadas}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CoordenadasCompletasValidator.class)
public @interface CoordenadasCompletas {

    String message() default "latitud y longitud deben informarse juntas";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
