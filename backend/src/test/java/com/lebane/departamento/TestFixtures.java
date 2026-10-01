package com.lebane.departamento;

import java.math.BigDecimal;

import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

/** Datos válidos de referencia para los tests; cada test modifica solo lo que verifica. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static DireccionRequest direccion() {
        return new DireccionRequest("Gorriti", "4850", "7", "B", "Ciudad Autónoma de Buenos Aires", "CABA", "C1414",
                new BigDecimal("-34.588900"), new BigDecimal("-58.430500"), null);
    }

    public static DepartamentoRequest departamento() {
        return departamento(3, 2, EstadoDepartamento.DISPONIBLE);
    }

    public static DepartamentoRequest departamento(int ambientes, int dormitorios, EstadoDepartamento estado) {
        return new DepartamentoRequest("3 ambientes en Palermo", "Luminoso, con balcón.", new BigDecimal("185000.00"),
                Moneda.USD, ambientes, dormitorios, 1, new BigDecimal("72.50"), estado, direccion());
    }

    public static ConsultaRequest consulta() {
        return new ConsultaRequest("Ana Pérez", "ana.perez@example.com", "+54 11 5555-0101",
                "¿Se puede visitar el sábado por la mañana?");
    }

    /** JSON válido de alta, para tests HTTP. */
    public static String departamentoJson() {
        return """
                {
                  "titulo": "3 ambientes en Palermo",
                  "descripcion": "Luminoso, con balcón.",
                  "precio": 185000.00,
                  "moneda": "USD",
                  "ambientes": 3,
                  "dormitorios": 2,
                  "banos": 1,
                  "superficieM2": 72.50,
                  "direccion": {
                    "calle": "Gorriti",
                    "numero": "4850",
                    "piso": "7",
                    "unidad": "B",
                    "ciudad": "Ciudad Autónoma de Buenos Aires",
                    "provincia": "CABA",
                    "codigoPostal": "C1414",
                    "latitud": -34.5889,
                    "longitud": -58.4305
                  }
                }
                """;
    }

    public static String consultaJson() {
        return """
                {
                  "nombre": "Ana Pérez",
                  "email": "ana.perez@example.com",
                  "telefono": "+54 11 5555-0101",
                  "mensaje": "¿Se puede visitar el sábado por la mañana?"
                }
                """;
    }
}
