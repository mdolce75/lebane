package com.lebane.departamento.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.lebane.exception.PreconditionFailedException;

class EntityTagsTest {

    @Test
    void formatsStrongEtag() {
        assertThat(EntityTags.of(7)).isEqualTo("\"7\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"*", "  "})
    void absentOrWildcardMeansNoPrecondition(String header) {
        assertThat(EntityTags.parseIfMatch(header)).isEmpty();
    }

    @Test
    void parsesSingleAndMultipleTags() {
        assertThat(EntityTags.parseIfMatch("\"3\"")).containsExactly(3L);
        assertThat(EntityTags.parseIfMatch("\"3\", W/\"4\"")).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void foreignTagNeverMatches() {
        assertThatThrownBy(() -> EntityTags.parseIfMatch("\"abc\"")).isInstanceOf(PreconditionFailedException.class);
    }
}
