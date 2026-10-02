package com.lebane.departamento.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

/**
 * Regla del proyecto: el listado y sus filtros se construyen solo con la Criteria API y el metamodelo estático.
 * Ninguna consulta escrita como texto (JPQL/SQL en {@code @Query}, {@code createQuery(String)},
 * {@code createNativeQuery}) ni armada concatenando cadenas en el código del listado.
 */
class ListadoSinConsultasDeTextoTest {

    private static final Path FUENTES = Path.of("src/main/java/com/lebane/departamento");

    /** Consultas como texto: APIs que reciben JPQL/SQL y literales que empiezan como una sentencia. */
    private static final Pattern CONSULTA_DE_TEXTO = Pattern.compile(
            "@Query|@NativeQuery|createNativeQuery|createQuery\\s*\\(\\s*\"|\"\"\"|\"\\s*(select|from|where|order by|join)\\b",
            Pattern.CASE_INSENSITIVE);

    @ParameterizedTest
    @ValueSource(strings = {
            "repository/DepartamentoListadoRepository.java",
            "repository/DepartamentoListadoRepositoryImpl.java",
            "repository/DepartamentoSpecifications.java",
            "repository/DepartamentoFiltro.java",
            "service/DepartamentoListadoService.java"
    })
    void elCodigoDelListadoNoTieneConsultasDeTexto(String archivo) throws IOException {
        String fuente = sinComentarios(Files.readString(FUENTES.resolve(archivo)));

        assertThat(CONSULTA_DE_TEXTO.matcher(fuente).find())
                .as("%s contiene una consulta escrita como texto: usar Criteria API + metamodelo", archivo)
                .isFalse();
    }

    @Test
    void elRepositorioDelListadoNoDeclaraConsultasConAnotaciones() {
        // El COUNT del listado usa JpaSpecificationExecutor.count(spec) (Criteria); ningún método del repositorio
        // del listado puede declarar JPQL/SQL.
        for (Class<?> repositorio : new Class<?>[] {DepartamentoListadoRepository.class}) {
            assertThat(Arrays.stream(repositorio.getMethods())
                    .filter(ListadoSinConsultasDeTextoTest::declaraConsulta)
                    .map(Method::getName))
                    .as("métodos con @Query/@NativeQuery en %s", repositorio.getSimpleName())
                    .isEmpty();
        }
    }

    private static boolean declaraConsulta(Method method) {
        return method.isAnnotationPresent(Query.class) || method.isAnnotationPresent(NativeQuery.class);
    }

    /** Los comentarios pueden mencionar SQL (p. ej. para explicar el plan) sin que eso sea una consulta. */
    private static String sinComentarios(String fuente) {
        return fuente.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
