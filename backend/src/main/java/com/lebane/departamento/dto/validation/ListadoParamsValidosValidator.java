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
        if (params.disponible() != null && !params.estado().isEmpty()) {
            valid = error(context, "disponible", "no se combina con estado: usá uno de los dos");
        }
        if (invertido(params.precioMin(), params.precioMax())) {
            valid = error(context, "precioMax", "debe ser mayor o igual que precioMin");
        }
        if (invertido(params.superficieMin(), params.superficieMax())) {
            valid = error(context, "superficieMax", "debe ser mayor o igual que superficieMin");
        }
        if (params.pagina() != null && params.cantidad() != null && params.pagina() >= 0 && params.cantidad() > 0
                && ((long) params.pagina() + 1) * params.cantidad() > DepartamentoListadoParams.MAX_RESULT_WINDOW) {
            valid = error(context, "pagina", "supera la ventana máxima de "
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
