package com.lebane.departamento.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

/**
 * Reglas de mapeo que protegen la performance: ninguna relación EAGER (el default de JPA para {@code @ManyToOne} y
 * {@code @OneToOne} es EAGER, por eso se verifica explícitamente) y ninguna colección mapeada en las entidades.
 */
class EntityMappingRulesTest {

    @ParameterizedTest
    @ValueSource(classes = {Departamento.class, Imagen.class, Consulta.class})
    void toOneAssociationsAreLazy(Class<?> entity) {
        assertThat(entity.isAnnotationPresent(Entity.class)).isTrue();
        for (Field field : entity.getDeclaredFields()) {
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
            OneToOne oneToOne = field.getAnnotation(OneToOne.class);
            if (manyToOne != null) {
                assertThat(manyToOne.fetch()).as("%s.%s", entity.getSimpleName(), field.getName())
                        .isEqualTo(FetchType.LAZY);
            }
            if (oneToOne != null) {
                assertThat(oneToOne.fetch()).as("%s.%s", entity.getSimpleName(), field.getName())
                        .isEqualTo(FetchType.LAZY);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {Departamento.class, Imagen.class, Consulta.class})
    void entitiesDoNotMapCollections(Class<?> entity) {
        for (Field field : entity.getDeclaredFields()) {
            assertThat(field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)
                    || Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType()))
                    .as("%s.%s no debe ser una colección mapeada", entity.getSimpleName(), field.getName())
                    .isFalse();
        }
    }
}
