package com.rzodeczko.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenType enum")
class TokenTypeTest {

    @Test
    @DisplayName("ACCESS toString returns 'access'")
    void accessToString() {
        assertThat(TokenType.ACCESS.toString()).isEqualTo("access");
    }

    @Test
    @DisplayName("REFRESH toString returns 'refresh'")
    void refreshToString() {
        assertThat(TokenType.REFRESH.toString()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("of('access') returns ACCESS")
    void ofAccess() {
        assertThat(TokenType.of("access")).isEqualTo(TokenType.ACCESS);
    }

    @Test
    @DisplayName("of('refresh') returns REFRESH")
    void ofRefresh() {
        assertThat(TokenType.of("refresh")).isEqualTo(TokenType.REFRESH);
    }

    @Test
    @DisplayName("of unknown value throws IllegalArgumentException")
    void ofUnknown() {
        assertThatThrownBy(() -> TokenType.of("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown token type");
    }
}
