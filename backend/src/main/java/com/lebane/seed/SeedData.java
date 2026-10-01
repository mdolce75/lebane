package com.lebane.seed;

import java.math.BigDecimal;
import java.util.List;

import com.lebane.departamento.dto.ConsultaRequest;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

/**
 * Datos representativos para desarrollo local. Ficticios: no corresponden a personas ni publicaciones reales y no
 * dependen de servicios externos. Las fotos se agregan con la integración de storage (Fase 4).
 */
final class SeedData {

    record SeedDepartamento(String codigo, DepartamentoRequest datos, List<ConsultaRequest> consultas) {
    }

    private SeedData() {
    }

    static List<SeedDepartamento> departamentos() {
        return List.of(
                seed("SEED-0001", "Luminoso 3 ambientes con balcón en Palermo",
                        "Frente, piso alto, cocina integrada y balcón corrido. A metros de Plaza Serrano.",
                        "185000", Moneda.USD, 3, 2, 1, "72.50", EstadoDepartamento.DISPONIBLE,
                        direccion("Gorriti", "4850", "7", "B", "Ciudad Autónoma de Buenos Aires", "CABA", "C1414",
                                "-34.588900", "-58.430500"),
                        consulta("Ana Pérez", "ana.perez@example.com", "+54 11 5555-0101",
                                "¿Se puede visitar el sábado por la mañana?"),
                        consulta("Martín Gómez", "martin.gomez@example.com", null,
                                "¿Acepta crédito hipotecario? Gracias.")),
                seed("SEED-0002", "Monoambiente a estrenar en Belgrano",
                        "Ideal inversión. Amenities: SUM, laundry y terraza con parrilla.",
                        "98000", Moneda.USD, 1, 0, 1, "32.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Av. Cabildo", "2230", "4", "C", "Ciudad Autónoma de Buenos Aires", "CABA", "C1428",
                                "-34.561200", "-58.456800"),
                        consulta("Lucía Fernández", "lucia.fernandez@example.com", "+54 11 5555-0102",
                                "¿Cuáles son las expensas mensuales?")),
                seed("SEED-0003", "Clásico 4 ambientes en Recoleta",
                        "Petit hotel reciclado, techos altos, pisos de roble y dependencia de servicio.",
                        "420000", Moneda.USD, 4, 3, 2, "145.00", EstadoDepartamento.RESERVADO,
                        direccion("Av. Alvear", "1650", "2", null, "Ciudad Autónoma de Buenos Aires", "CABA", "C1014",
                                "-34.587600", "-58.389100")),
                seed("SEED-0004", "2 ambientes con patio en Caballito",
                        "Planta baja al contrafrente, patio propio de 20 m², muy silencioso.",
                        "112000", Moneda.USD, 2, 1, 1, "48.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Hidalgo", "740", "PB", "A", "Ciudad Autónoma de Buenos Aires", "CABA", "C1405",
                                null, null),
                        consulta("Diego Ramírez", "diego.ramirez@example.com", "011 5555-0103",
                                "¿El patio tiene salida independiente?"),
                        consulta("Sofía Torres", "sofia.torres@example.com", null,
                                "Me interesa, ¿se aceptan mascotas en el edificio?"),
                        consulta("Pablo Díaz", "pablo.diaz@example.com", "+54 9 11 5555-0104",
                                "¿Hay posibilidad de cochera opcional?")),
                seed("SEED-0005", "3 ambientes con cochera en Villa Urquiza",
                        "Edificio de 2019, cochera cubierta y baulera. Cerca del subte B.",
                        "165000", Moneda.USD, 3, 2, 2, "68.00", EstadoDepartamento.VENDIDO,
                        direccion("Av. Triunvirato", "4500", "9", "D", "Ciudad Autónoma de Buenos Aires", "CABA",
                                "C1431", "-34.574300", "-58.485900")),
                seed("SEED-0006", "Dúplex con terraza en Núñez",
                        "Último piso con terraza propia, parrilla y vista abierta al río.",
                        "275000", Moneda.USD, 4, 2, 2, "110.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Av. del Libertador", "7700", "11", null, "Ciudad Autónoma de Buenos Aires", "CABA",
                                "C1429", "-34.545100", "-58.462200"),
                        consulta("Valentina Ruiz", "valentina.ruiz@example.com", null,
                                "¿La terraza es de uso exclusivo?")),
                seed("SEED-0007", "2 ambientes reciclado en San Telmo",
                        "Edificio histórico, balcón francés a la calle empedrada.",
                        "89000", Moneda.USD, 2, 1, 1, "45.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Defensa", "1020", "1", "3", "Ciudad Autónoma de Buenos Aires", "CABA", "C1065",
                                "-34.619900", "-58.371700")),
                seed("SEED-0008", "Departamento familiar en Almagro",
                        "Tres dormitorios, living comedor amplio y lavadero independiente.",
                        "198000000", Moneda.ARS, 4, 3, 1, "92.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Av. Rivadavia", "3800", "6", "A", "Ciudad Autónoma de Buenos Aires", "CABA", "C1204",
                                "-34.610400", "-58.419800")),
                seed("SEED-0009", "Frente al río en Rosario",
                        "Vista al Paraná, amenities completos y seguridad 24 h.",
                        "230000", Moneda.USD, 3, 2, 2, "85.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Av. de la Costa Estanislao López", "2600", "15", "B", "Rosario", "Santa Fe",
                                "S2000", "-32.929800", "-60.640200"),
                        consulta("Joaquín Sosa", "joaquin.sosa@example.com", "+54 341 555-0105",
                                "¿El precio incluye la cochera?")),
                seed("SEED-0010", "2 ambientes en Nueva Córdoba",
                        "A dos cuadras del Parque Sarmiento, ideal estudiantes.",
                        "76000000", Moneda.ARS, 2, 1, 1, "42.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Bv. Chacabuco", "650", "5", "C", "Córdoba", "Córdoba", "X5000",
                                "-31.424100", "-64.184600")),
                seed("SEED-0011", "Monoambiente en el centro de Mendoza",
                        "Cerca de la peatonal Sarmiento, edificio con pileta.",
                        "54000", Moneda.USD, 1, 0, 1, "30.00", EstadoDepartamento.RESERVADO,
                        direccion("Av. San Martín", "1200", "8", "F", "Mendoza", "Mendoza", "M5500",
                                "-32.889500", "-68.844000")),
                seed("SEED-0012", "Semipiso con vista al mar en Mar del Plata",
                        "Living con ventanal al mar, dos dormitorios en suite.",
                        "205000", Moneda.USD, 3, 2, 2, "95.00", EstadoDepartamento.DISPONIBLE,
                        direccion("Bv. Marítimo Patricio Peralta Ramos", "2300", "12", null, "Mar del Plata",
                                "Buenos Aires", "B7600", "-38.008900", "-57.537800"),
                        consulta("Camila Herrera", "camila.herrera@example.com", null,
                                "¿Está disponible para entrega inmediata?")));
    }

    private static SeedDepartamento seed(String codigo, String titulo, String descripcion, String precio,
            Moneda moneda, int ambientes, int dormitorios, int banos, String superficie, EstadoDepartamento estado,
            DireccionRequest direccion, ConsultaRequest... consultas) {
        DepartamentoRequest datos = new DepartamentoRequest(titulo, descripcion, new BigDecimal(precio), moneda,
                ambientes, dormitorios, banos, new BigDecimal(superficie), estado, direccion);
        return new SeedDepartamento(codigo, datos, List.of(consultas));
    }

    private static DireccionRequest direccion(String calle, String numero, String piso, String unidad, String ciudad,
            String provincia, String codigoPostal, String latitud, String longitud) {
        return new DireccionRequest(calle, numero, piso, unidad, ciudad, provincia, codigoPostal,
                latitud == null ? null : new BigDecimal(latitud), longitud == null ? null : new BigDecimal(longitud),
                null);
    }

    private static ConsultaRequest consulta(String nombre, String email, String telefono, String mensaje) {
        return new ConsultaRequest(nombre, email, telefono, mensaje);
    }
}
