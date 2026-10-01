package com.lebane.departamento.dto.validation;

import com.lebane.departamento.dto.DepartamentoRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DormitoriosMenoresQueAmbientesValidator
        implements ConstraintValidator<DormitoriosMenoresQueAmbientes, DepartamentoRequest> {

    @Override
    public boolean isValid(DepartamentoRequest request, ConstraintValidatorContext context) {
        if (request == null || request.ambientes() == null || request.dormitorios() == null) {
            return true; // @NotNull de cada campo informa los faltantes
        }
        if (request.dormitorios() < request.ambientes()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("dormitorios")
                .addConstraintViolation();
        return false;
    }
}
