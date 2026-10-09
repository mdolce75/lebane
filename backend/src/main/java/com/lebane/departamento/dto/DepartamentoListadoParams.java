package com.lebane.departamento.dto;

import java.math.BigDecimal;
import java.util.List;

import com.lebane.departamento.dto.validation.ListadoParamsValidos;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Moneda;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query params de {@code GET /api/departamentos}. Todos opcionales; los textos se normalizan (sin espacios
 * sobrantes, vacío = sin filtro) antes de validar.
 *
 * @param q       texto contenido en el título (3 a 100 caracteres: con menos, un índice de trigramas no sirve)
 * @param estado      uno o más estados ({@code estado=DISPONIBLE&estado=RESERVADO} o separados por coma)
 * @param disponible  atajo del enunciado: {@code true} = solo disponibles; {@code false} = reservados o vendidos
 * @param pagina      página, desde 0
 * @param cantidad    tamaño de página, 1..{@value #MAX_SIZE}
 * @param sort    {@code createdAt|precio|superficieM2}, opcionalmente {@code ,asc|,desc}
 */
@ListadoParamsValidos
// Los filtros no llevan example a propósito: Swagger UI los precarga en "Try it out" y el listado saldría filtrado
// (y probablemente vacío) sin que el usuario lo pida.
@Schema(description = "Filtros, orden y paginación del listado. Todos opcionales.")
public record DepartamentoListadoParams(
        @Schema(description = "Texto a buscar en el título, sin distinguir mayúsculas ni acentos (balcon encuentra balcón); mínimo 3 caracteres")
        @Size(min = 3, max = 100) String q,
        @Schema(description = "Ciudad exacta (sin distinguir mayúsculas)")
        @Size(max = 80) String ciudad,
        @Schema(description = "Estados a incluir; repetir el parámetro para varios (estado=DISPONIBLE&estado=RESERVADO)")
        List<EstadoDepartamento> estado,
        @Schema(description = "true: solo los disponibles; false: solo los reservados o vendidos. No se combina con estado")
        Boolean disponible,
        @Schema(description = "Moneda del filtro de precio (ARS y USD no son comparables). Si se filtra por precio sin indicarla, se usa USD")
        Moneda moneda,
        @Schema(description = "Precio mínimo, en la moneda indicada (USD si no se indica)")
        @PositiveOrZero BigDecimal precioMin,
        @Schema(description = "Precio máximo, en la moneda indicada (USD si no se indica); mayor o igual que precioMin")
        @PositiveOrZero BigDecimal precioMax,
        @Schema(description = "Ambientes mínimos")
        @Min(1) @Max(20) Integer ambientesMin,
        @Schema(description = "Dormitorios mínimos")
        @Min(0) @Max(19) Integer dormitoriosMin,
        @Schema(description = "Baños mínimos")
        @Min(1) @Max(10) Integer banosMin,
        @Schema(description = "Superficie mínima en m²")
        @PositiveOrZero BigDecimal superficieMin,
        @Schema(description = "Superficie máxima en m² (mayor o igual que superficieMin)")
        @PositiveOrZero BigDecimal superficieMax,
        @Schema(description = "true: solo con fotos; false: solo sin fotos; omitido: todos")
        Boolean conImagenes,
        @Schema(description = "true: solo los dados de baja (para reactivarlos); omitido o false: solo los publicados")
        Boolean dadosDeBaja,
        @Schema(description = "Página, desde 0. (pagina + 1) × cantidad no puede superar 10.000", example = "0", defaultValue = "0")
        @Min(0) Integer pagina,
        @Schema(description = "Cantidad de departamentos por página", example = "20", defaultValue = "20")
        @Min(1) @Max(MAX_SIZE) Integer cantidad,
        @Pattern(regexp = CampoOrden.PATTERN, message = "debe ser createdAt, precio o superficieM2, con ,asc o ,desc opcional")
        @Schema(description = "Orden: createdAt, precio o superficieM2, con ,asc o ,desc. El precio se ordena dentro de cada moneda; siempre se desempata por id", defaultValue = "createdAt,desc")
        String sort) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    /**
     * Ventana máxima de resultados navegables ({@code (page + 1) * size}). Con OFFSET, PostgreSQL recorre y descarta
     * todas las filas anteriores: páginas muy profundas son caras. Más allá, se pide refinar los filtros.
     */
    public static final int MAX_RESULT_WINDOW = 10_000;

    public DepartamentoListadoParams {
        q = normalizar(q);
        ciudad = normalizar(ciudad);
        estado = estado == null ? List.of() : estado.stream().filter(e -> e != null).toList();
        pagina = pagina == null ? 0 : pagina;
        cantidad = cantidad == null ? DEFAULT_SIZE : cantidad;
        // ARS y USD no son comparables: un filtro de precio sin moneda se interpreta en USD, la moneda habitual de
        // las publicaciones de venta.
        if (moneda == null && (precioMin != null || precioMax != null)) {
            moneda = Moneda.USD;
        }
        sort = normalizar(sort) == null ? CampoOrden.DEFAULT : sort.strip();
    }

    private static String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
