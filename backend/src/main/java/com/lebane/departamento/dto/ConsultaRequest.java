package com.lebane.departamento.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** Consulta de un interesado. Contiene datos personales: no se registra en logs. */
@Schema(description = "Consulta de un interesado. Los datos personales no se registran en logs.")
public record ConsultaRequest(
        @Schema(description = "Nombre de quien consulta", example = "Ana Pérez")
        @NotBlank @Size(max = 100) String nombre,
        @Schema(description = "Email de contacto", example = "ana@example.com")
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()\\-]{6,30}$", message = "no es un teléfono válido")
        @Schema(description = "Teléfono de contacto (dígitos, espacios, +, -, paréntesis)", example = "+54 11 5555-1234")
        String telefono,
        @Schema(description = "Mensaje", example = "¿Se puede visitar el sábado por la mañana?")
        @NotBlank @Size(min = 10, max = 2000) String mensaje) {

    @Override
    public String toString() {
        return "ConsultaRequest[datos personales omitidos]";
    }
}
