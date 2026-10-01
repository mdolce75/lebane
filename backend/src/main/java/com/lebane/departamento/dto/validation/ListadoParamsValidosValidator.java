package com.lebane.departamento.dto.validation;

import java.math.BigDecimal;

import com.lebane.departamento.dto.DepartamentoListadoParams;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ListadoParamsValidosValidator
        implements ConstraintValidator<ListadoParamsValidos, DepartamentoListadoParams> {

    @Override
    public boolean isValid(DepartamentoListadoParams params, ConstraintValidatorContext context) {
        if (params == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if ((params.precioMin() != null || params.precioMax() != null) && params.moneda() == null) {
            valid = error(context, "moneda", "es obligatoria para filtrar por precio");
        }
        if (invertido(params.precioMin(), params.precioMax())) {
            valid = error(context, "precioMax", "debe ser mayor o igual que precioMin");
        }
        if (invertido(params.superficieMin(), params.superficieMax())) {
            valid = error(context, "superficieMax", "debe ser mayor o igual que superficieMin");
        }
        if (params.page() != null && params.size() != null && params.page() >= 0 && params.size() > 0
                && ((long) params.page() + 1) * params.size() > DepartamentoListadoParams.MAX_RESULT_WINDOW) {
            valid = error(context, "page", "supera la ventana máxima de "
                    + DepartamentoListadoParams.MAX_RESULT_WINDOW + " resultados; refiná los filtros");
        }
        return valid;
    }

    private static boolean invertido(BigDecimal min, BigDecimal max) {
        return min != null && max != null && min.compareTo(max) > 0;
    }

    private static boolean error(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        return false;
    }
}
