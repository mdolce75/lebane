package com.lebane.seed;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lebane.departamento.dto.ConsultaRequest;

/**
 * Consultas de ejemplo adicionales para los avisos del seed, para que el listado y el detalle muestren actividad
 * realista. Ficticias: nombres combinados al azar, emails en el dominio reservado {@code example.com} y teléfonos
 * con prefijo de prueba.
 *
 * <p>Determinístico: cada aviso recibe siempre las mismas consultas (de 3 a 8, según su código). El email es
 * único por aviso y posición ({@code nombre.apellido.s0001-03@example.com}), lo que permite al seeder saber cuáles
 * ya existen y agregar solo las que faltan, también en bases que ya tenían el seed.
 */
final class SeedConsultas {

    static final int MINIMO = 3;
    static final int MAXIMO = 8;

    private static final List<String> NOMBRES = List.of(
            "Ana", "Martín", "Lucía", "Diego", "Sofía", "Pablo", "Valentina", "Joaquín", "Camila", "Tomás",
            "Florencia", "Nicolás", "Julieta", "Matías", "Agustina", "Federico", "Carolina", "Santiago");

    private static final List<String> APELLIDOS = List.of(
            "González", "Rodríguez", "Fernández", "López", "Martínez", "García", "Pérez", "Sánchez", "Romero",
            "Sosa", "Álvarez", "Torres", "Ruiz", "Benítez", "Acosta", "Medina", "Herrera", "Suárez");

    private static final List<String> MENSAJES = List.of(
            "Hola, ¿sigue disponible? Me gustaría coordinar una visita esta semana.",
            "¿Acepta crédito hipotecario? Tengo preaprobado un préstamo del banco.",
            "Buenas tardes. ¿Cuánto son las expensas mensuales y qué incluyen?",
            "¿El precio es negociable? Estoy buscando para mudarme en dos meses.",
            "¿Se puede visitar el sábado por la mañana? Quedo atento.",
            "Me interesa mucho. ¿Tiene cochera o se puede alquilar una en el edificio?",
            "¿Aceptan permuta por un departamento más chico en la misma zona?",
            "Hola, ¿el edificio admite mascotas? Tengo un perro mediano.",
            "¿Cuál es la orientación del departamento? ¿Entra sol por la tarde?",
            "Consulto por el estado de las instalaciones: ¿gas, agua y electricidad están actualizados?",
            "¿Está apto para escriturar? Necesito saber si tiene la documentación al día.",
            "Busco algo para inversión. ¿Sabe cuánto se podría alquilar por mes?",
            "¿El departamento se entrega con muebles o vacío?",
            "Hola, vi el aviso y me interesa. ¿Me pueden mandar más fotos de la cocina y el baño?",
            "¿Qué tan lejos queda del transporte público? Trabajo en el centro.",
            "¿Hay seguridad o encargado en el edificio? ¿Cuántas unidades tiene?");

    private SeedConsultas() {
    }

    /** Cantidad de consultas de ejemplo para un aviso del seed: de {@value #MINIMO} a {@value #MAXIMO}. */
    static int cantidadPara(String codigo) {
        return MINIMO + (numero(codigo) * 7) % (MAXIMO - MINIMO + 1);
    }

    static List<ConsultaRequest> para(String codigo) {
        int numero = numero(codigo);
        int cantidad = cantidadPara(codigo);
        List<ConsultaRequest> consultas = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            // Primos distintos para que nombres, apellidos y mensajes no se repitan en el mismo orden entre avisos.
            String nombre = NOMBRES.get((numero * 5 + i * 7) % NOMBRES.size());
            String apellido = APELLIDOS.get((numero * 11 + i * 3) % APELLIDOS.size());
            String mensaje = MENSAJES.get((numero * 3 + i * 5) % MENSAJES.size());
            String email = "%s.%s.s%04d-%02d@example.com".formatted(ascii(nombre), ascii(apellido), numero, i + 1);
            // Uno de cada tres sin teléfono (es opcional).
            String telefono = (numero + i) % 3 == 0 ? null : "+54 11 5555-%02d%02d".formatted(numero % 100, i + 1);
            consultas.add(new ConsultaRequest(nombre + " " + apellido, email, telefono, mensaje));
        }
        return consultas;
    }

    private static int numero(String codigo) {
        return Integer.parseInt(codigo.substring(codigo.indexOf('-') + 1));
    }

    /** Minúsculas sin acentos, para la parte local del email. */
    private static String ascii(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
