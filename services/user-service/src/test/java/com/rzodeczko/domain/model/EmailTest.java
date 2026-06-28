package com.rzodeczko.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Email value object")
class EmailTest {

    @Test
    @DisplayName("should create valid email")
    void shouldCreateValidEmail() {
        var email = new Email("john@example.com");

        assertThat(email.value()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("should support equality based on value")
    void shouldSupportEquality() {
        var email1 = new Email("john@example.com");
        var email2 = new Email("john@example.com");

        assertThat(email1).isEqualTo(email2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("should reject blank or null values")
    void shouldRejectBlankOrNull(String value) {
        assertThatThrownBy(() -> new Email(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("should reject email without @")
    void shouldRejectEmailWithoutAtSign() {
        assertThatThrownBy(() -> new Email("invalid-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@domain.com", "a@b", "test+tag@example.org"})
    @DisplayName("should accept emails containing @")
    void shouldAcceptEmailsWithAtSign(String value) {
        var email = new Email(value);

        assertThat(email.value()).isEqualTo(value);
    }
}
