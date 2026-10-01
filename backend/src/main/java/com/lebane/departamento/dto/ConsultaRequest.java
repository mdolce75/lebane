package com.lebane.departamento.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Consulta de un interesado. Contiene datos personales: no se registra en logs. */
public record ConsultaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()\\-]{6,30}$", message = "no es un teléfono válido")
        String telefono,
        @NotBlank @Size(min = 10, max = 2000) String mensaje) {

    @Override
    public String toString() {
        return "ConsultaRequest[datos personales omitidos]";
    }
}
