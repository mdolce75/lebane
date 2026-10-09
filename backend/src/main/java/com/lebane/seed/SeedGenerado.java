package com.lebane.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;
import com.lebane.seed.SeedData.SeedDepartamento;

/**
 * Avisos generados para completar el volumen de datos de prueba ({@value SeedData#TOTAL} en total, junto con los
 * escritos a mano de {@link SeedData}). Ficticios y determinísticos: el aviso N siempre tiene los mismos datos, así
 * que el seed sigue siendo idempotente y reproducible.
 *
 * <p>Variados a propósito para que los filtros y ordenamientos del listado muestren resultados distintos: ciudades,
 * ambientes de 1 a 5, superficies de ~25 a ~140 m², precios en USD y ARS, y algunos reservados o vendidos. La
 * dirección nunca se repite (la altura depende de N), igual que exige la regla de avisos duplicados.
 */
final class SeedGenerado {

    private record Ciudad(String nombre, String provincia, String codigoPostal, double latitud, double longitud,
            List<String> barrios, List<String> calles) {
    }

    private static final List<Ciudad> CIUDADES = List.of(
            new Ciudad("Ciudad Autónoma de Buenos Aires", "CABA", "C1414", -34.6037, -58.3816,
                    List.of("Palermo", "Belgrano", "Recoleta", "Caballito", "Almagro", "Villa Crespo", "Núñez",
                            "Colegiales", "San Telmo", "Villa Urquiza", "Flores", "Boedo"),
                    List.of("Av. Corrientes", "Av. Santa Fe", "Av. Rivadavia", "Av. Cabildo", "Gorriti", "Honduras",
                            "Thames", "Av. Córdoba", "Billinghurst", "Malabia", "Av. Scalabrini Ortiz", "Charcas")),
            new Ciudad("Rosario", "Santa Fe", "S2000", -32.9468, -60.6393,
                    List.of("Centro", "Pichincha", "Fisherton", "Echesortu", "Barrio Martin"),
                    List.of("Bv. Oroño", "Córdoba", "San Lorenzo", "Mitre", "Entre Ríos", "Av. Pellegrini")),
            new Ciudad("Córdoba", "Córdoba", "X5000", -31.4201, -64.1888,
                    List.of("Nueva Córdoba", "Güemes", "General Paz", "Cerro de las Rosas", "Alberdi"),
                    List.of("Bv. Chacabuco", "Obispo Trejo", "Av. Colón", "Independencia", "Belgrano")),
            new Ciudad("Mendoza", "Mendoza", "M5500", -32.8895, -68.8458,
                    List.of("Quinta Sección", "Sexta Sección", "Centro", "Barrio Bombal"),
                    List.of("Av. San Martín", "Av. Arístides Villanueva", "Sarmiento", "Belgrano", "Chile")),
            new Ciudad("Mar del Plata", "Buenos Aires", "B7600", -38.0055, -57.5426,
                    List.of("La Perla", "Playa Grande", "Centro", "Los Troncos", "Plaza Mitre"),
                    List.of("Bv. Marítimo Patricio Peralta Ramos", "Av. Colón", "Güemes", "Alem", "San Martín")),
            new Ciudad("La Plata", "Buenos Aires", "B1900", -34.9214, -57.9545,
                    List.of("Centro", "La Loma", "Barrio Norte", "Plaza Moreno"),
                    List.of("Calle 7", "Calle 50", "Diagonal 74", "Calle 12", "Av. 51")),
            new Ciudad("San Miguel de Tucumán", "Tucumán", "T4000", -26.8083, -65.2176,
                    List.of("Barrio Norte", "Centro", "Yerba Buena"),
                    List.of("Av. Mate de Luna", "24 de Septiembre", "San Martín", "Congreso")),
            new Ciudad("Neuquén", "Neuquén", "Q8300", -38.9516, -68.0591,
                    List.of("Centro", "Área Centro Este", "Santa Genoveva"),
                    List.of("Av. Argentina", "Av. Olascoaga", "Belgrano", "Alberdi")));

    private static final List<String> CARACTERISTICAS = List.of(
            "luminoso", "con balcón", "a estrenar", "reciclado", "con cochera", "con patio", "al frente",
            "con amenities", "con vista abierta", "en esquina", "contrafrente silencioso", "con terraza");

    private static final List<String> DESCRIPCIONES = List.of(
            "Cocina integrada, pisos de madera y muy buena luz natural durante todo el día.",
            "Edificio con seguridad, SUM y laundry. A metros del transporte público y comercios.",
            "Ambientes amplios, placares en todos los dormitorios y balcón aterrazado.",
            "Totalmente reciclado: instalaciones nuevas, aberturas de aluminio y aire acondicionado.",
            "Unidad al contrafrente, muy silenciosa, con lavadero independiente.",
            "Ideal inversión: alta demanda de alquiler en la zona. Apto crédito.",
            "Expensas bajas, edificio de pocas unidades y encargado permanente.");

    private SeedGenerado() {
    }

    /** Avisos {@code SEED-desde} a {@code SEED-hasta}, ambos incluidos. */
    static List<SeedDepartamento> departamentos(int desde, int hasta) {
        List<SeedDepartamento> avisos = new ArrayList<>(hasta - desde + 1);
        for (int n = desde; n <= hasta; n++) {
            avisos.add(aviso(n));
        }
        return avisos;
    }

    static SeedDepartamento aviso(int n) {
        Ciudad ciudad = CIUDADES.get(n % CIUDADES.size());
        String barrio = ciudad.barrios().get((n / CIUDADES.size()) % ciudad.barrios().size());
        String calle = ciudad.calles().get((n * 7 / CIUDADES.size()) % ciudad.calles().size());

        int ambientes = 1 + (n * 3) % 5;
        // Menos dormitorios que ambientes (un monoambiente tiene 0); a veces un ambiente más de living o estudio.
        int dormitorios = ambientes == 1 ? 0 : ambientes - 1 - (ambientes >= 3 && n % 3 == 0 ? 1 : 0);
        int banos = ambientes >= 4 ? 2 : 1;
        BigDecimal superficie = BigDecimal.valueOf(22 + ambientes * 20L + (n * 13L) % 25)
                .add(n % 2 == 0 ? new BigDecimal("0.50") : BigDecimal.ZERO);

        // Precio por m² entre 1.600 y 3.300 USD según la zona; algunos publicados en pesos.
        long usdPorM2 = 1_600 + (n * 37L) % 1_700;
        BigDecimal precioUsd = superficie.multiply(BigDecimal.valueOf(usdPorM2))
                .divide(BigDecimal.valueOf(500), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(500));
        Moneda moneda = n % 6 == 0 ? Moneda.ARS : Moneda.USD;
        BigDecimal precio = moneda == Moneda.ARS ? precioUsd.multiply(BigDecimal.valueOf(1_200)) : precioUsd;

        EstadoDepartamento estado = n % 17 == 0 ? EstadoDepartamento.VENDIDO
                : n % 9 == 0 ? EstadoDepartamento.RESERVADO
                : EstadoDepartamento.DISPONIBLE;

        String tipo = ambientes == 1 ? "Monoambiente" : ambientes + " ambientes";
        String titulo = "%s %s en %s".formatted(tipo, CARACTERISTICAS.get(n % CARACTERISTICAS.size()), barrio);
        String descripcion = DESCRIPCIONES.get((n * 5) % DESCRIPCIONES.size());

        // La altura depende solo de N: dos avisos nunca comparten dirección.
        String numero = String.valueOf(100 + n * 9);
        String piso = n % 7 == 0 ? "PB" : String.valueOf(1 + (n * 11) % 14);
        String unidad = String.valueOf((char) ('A' + n % 6));
        DireccionRequest direccion = new DireccionRequest(calle, numero, piso, unidad, ciudad.nombre(),
                ciudad.provincia(), ciudad.codigoPostal(),
                coordenada(ciudad.latitud(), n * 31), coordenada(ciudad.longitud(), n * 17), null);

        DepartamentoRequest datos = new DepartamentoRequest(titulo, descripcion, precio, moneda, ambientes,
                dormitorios, banos, superficie, estado, direccion);
        return new SeedDepartamento("SEED-%04d".formatted(n), datos, List.of());
    }

    /** Desplazamiento de hasta ±0,02° (~2 km) alrededor del centro de la ciudad. */
    private static BigDecimal coordenada(double centro, int semilla) {
        double desplazamiento = ((semilla % 401) - 200) / 10_000.0;
        return BigDecimal.valueOf(centro + desplazamiento).setScale(6, RoundingMode.HALF_UP);
    }
}
