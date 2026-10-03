package com.lebane.departamento.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;

/**
 * Regla del proyecto: ninguna consulta escrita como texto. Todas las consultas (listado, filtros, reglas de negocio,
 * seed) se construyen con la Criteria API y el metamodelo estático: ni JPQL/SQL en {@code @Query}, ni
 * {@code createQuery(String)}, ni SQL nativo, ni sentencias armadas concatenando cadenas. Tampoco consultas derivadas
 * del nombre del método: los repositorios solo exponen métodos {@code default} sobre Specifications o fragmentos
 * implementados con Criteria.
 */
class SinConsultasDeTextoTest {

    private static final Path FUENTES = Path.of("src/main/java");
    private static final Path PERSISTENCIA = FUENTES.resolve("com/lebane/departamento/repository");

    /** APIs que reciben JPQL/SQL y literales que empiezan como una sentencia (palabra clave seguida de más texto). */
    private static final Pattern CONSULTA_DE_TEXTO = Pattern.compile(
            "@Query|@NativeQuery|createNativeQuery|createQuery\\s*\\(\\s*\"|JdbcTemplate|JdbcClient"
                    + "|\"\\s*(select|insert|update|delete|from|where|order by|join)\\s+[\\w*(]",
            Pattern.CASE_INSENSITIVE);

    /** En la capa de persistencia tampoco hay text blocks: no hay nada que escribir como texto. */
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"");

    @Test
    void ningunArchivoDelBackendTieneConsultasDeTexto() throws IOException {
        assertThat(archivosConCoincidencias(FUENTES, CONSULTA_DE_TEXTO))
                .as("consultas escritas como texto: usar Criteria API + metamodelo")
                .isEmpty();
    }

    @Test
    void laCapaDePersistenciaNoTieneTextBlocks() throws IOException {
        assertThat(archivosConCoincidencias(PERSISTENCIA, TEXT_BLOCK)).isEmpty();
    }

    @Test
    void losRepositoriosNoDeclaranConsultasDerivadasNiAnotadas() throws ClassNotFoundException {
        List<String> repositorios = repositoriosDeLaAplicacion();
        assertThat(repositorios).as("se encontraron los repositorios").isNotEmpty();

        for (String nombre : repositorios) {
            Class<?> repositorio = Class.forName(nombre);
            // Un método abstracto propio del repositorio sería una consulta derivada del nombre o anotada con
            // @Query. Los métodos default (Specifications) y los heredados de Spring Data o de los fragmentos
            // (implementados con Criteria) están permitidos.
            assertThat(Arrays.stream(repositorio.getDeclaredMethods())
                    .filter(method -> Modifier.isAbstract(method.getModifiers()))
                    .map(Method::getName))
                    .as("métodos abstractos (consultas derivadas o @Query) en %s", repositorio.getSimpleName())
                    .isEmpty();
        }
    }

    private static List<String> repositoriosDeLaAplicacion() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        return scanner.findCandidateComponents("com.lebane").stream().map(BeanDefinition::getBeanClassName).toList();
    }

    private static List<String> archivosConCoincidencias(Path raiz, Pattern patron) throws IOException {
        List<String> encontrados = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(raiz)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = patron.matcher(sinComentarios(Files.readString(archivo)));
                while (matcher.find()) {
                    encontrados.add(FUENTES.relativize(archivo) + ": " + matcher.group());
                }
            }
        }
        return encontrados;
    }

    /** Los comentarios pueden mencionar SQL (p. ej. para explicar el plan) sin que eso sea una consulta. */
    private static String sinComentarios(String fuente) {
        return fuente.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
