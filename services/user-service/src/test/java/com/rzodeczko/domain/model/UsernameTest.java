package com.rzodeczko.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Username value object")
class UsernameTest {

    @Test
    @DisplayName("should create valid username")
    void shouldCreateValidUsername() {
        var username = new Username("john");

        assertThat(username.value()).isEqualTo("john");
    }

    @Test
    @DisplayName("should support equality based on value")
    void shouldSupportEquality() {
        var u1 = new Username("john");
        var u2 = new Username("john");

        assertThat(u1).isEqualTo(u2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("should reject blank or null values")
    void shouldRejectBlankOrNull(String value) {
        assertThatThrownBy(() -> new Username(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("should reject username shorter than 3 characters")
    void shouldRejectTooShort() {
        assertThatThrownBy(() -> new Username("ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 3 and 50");
    }

    @Test
    @DisplayName("should reject username longer than 50 characters")
    void shouldRejectTooLong() {
        String longName = "a".repeat(51);

        assertThatThrownBy(() -> new Username(longName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 3 and 50");
    }

    @Test
    @DisplayName("should accept username with exactly 3 characters")
    void shouldAcceptMinLength() {
        var username = new Username("abc");

        assertThat(username.value()).isEqualTo("abc");
    }

    @Test
    @DisplayName("should accept username with exactly 50 characters")
    void shouldAcceptMaxLength() {
        String name = "a".repeat(50);
        var username = new Username(name);

        assertThat(username.value()).isEqualTo(name);
    }
}
