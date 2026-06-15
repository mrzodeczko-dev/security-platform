package com.rzodeczko.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordEncoderAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordEncoderAdapter adapter;

    @Nested
    @DisplayName("encode")
    class Encode {

        @Test
        @DisplayName("should delegate encoding to PasswordEncoder")
        void shouldDelegateToPasswordEncoder() {
            // given
            var rawPassword = "my-secret-password";
            var encodedPassword = "$2a$10$encodedHash";
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

            // when
            var result = adapter.encode(rawPassword);

            // then
            assertThat(result).isEqualTo(encodedPassword);
            verify(passwordEncoder).encode(rawPassword);
        }
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("should return true when passwords match")
        void shouldReturnTrueWhenPasswordsMatch() {
            // given
            var rawPassword = "my-secret-password";
            var encodedPassword = "$2a$10$encodedHash";
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

            // when
            var result = adapter.matches(rawPassword, encodedPassword);

            // then
            assertThat(result).isTrue();
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }

        @Test
        @DisplayName("should return false when passwords do not match")
        void shouldReturnFalseWhenPasswordsDoNotMatch() {
            // given
            var rawPassword = "wrong-password";
            var encodedPassword = "$2a$10$encodedHash";
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

            // when
            var result = adapter.matches(rawPassword, encodedPassword);

            // then
            assertThat(result).isFalse();
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }
    }
}
