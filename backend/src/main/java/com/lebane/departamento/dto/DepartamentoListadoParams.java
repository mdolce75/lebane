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

/**
 * Query params de {@code GET /api/v1/departamentos}. Todos opcionales; los textos se normalizan (sin espacios
 * sobrantes, vacío = sin filtro) antes de validar.
 *
 * @param q       texto contenido en el título (3 a 100 caracteres: con menos, un índice de trigramas no sirve)
 * @param estado  uno o más estados ({@code estado=DISPONIBLE&estado=RESERVADO} o separados por coma)
 * @param page    página, desde 0
 * @param size    tamaño de página, 1..{@value #MAX_SIZE}
 * @param sort    {@code createdAt|precio|superficieM2}, opcionalmente {@code ,asc|,desc}
 */
@ListadoParamsValidos
public record DepartamentoListadoParams(
        @Size(min = 3, max = 100) String q,
        @Size(max = 80) String ciudad,
        List<EstadoDepartamento> estado,
        Moneda moneda,
        @PositiveOrZero BigDecimal precioMin,
        @PositiveOrZero BigDecimal precioMax,
        @Min(1) @Max(20) Integer ambientesMin,
        @Min(0) @Max(19) Integer dormitoriosMin,
        @Min(1) @Max(10) Integer banosMin,
        @PositiveOrZero BigDecimal superficieMin,
        @PositiveOrZero BigDecimal superficieMax,
        Boolean conImagenes,
        @Min(0) Integer page,
        @Min(1) @Max(MAX_SIZE) Integer size,
        @Pattern(regexp = CampoOrden.PATTERN, message = "debe ser createdAt, precio o superficieM2, con ,asc o ,desc opcional")
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
        page = page == null ? 0 : page;
        size = size == null ? DEFAULT_SIZE : size;
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
