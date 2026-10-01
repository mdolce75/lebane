package com.lebane.departamento.dto.validation;

import com.lebane.departamento.dto.DireccionRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordenadasCompletasValidator implements ConstraintValidator<CoordenadasCompletas, DireccionRequest> {

    @Override
    public boolean isValid(DireccionRequest direccion, ConstraintValidatorContext context) {
        if (direccion == null || (direccion.latitud() == null) == (direccion.longitud() == null)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(direccion.latitud() == null ? "latitud" : "longitud")
                .addConstraintViolation();
        return false;
    }
}
