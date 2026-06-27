package com.rzodeczko.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationCode domain model")
class VerificationCodeTest {

    @Nested
    @DisplayName("Constructor without id")
    class ThreeArgConstructor {

        @Test
        @DisplayName("should have id set to null")
        void shouldHaveNullId() {
            UUID userId = UUID.randomUUID();
            VerificationCode code = new VerificationCode("ABC123", 999999999999L, userId);

            assertThat(code.getId()).isNull();
        }

        @Test
        @DisplayName("should set code, expiresAt, and userId correctly")
        void shouldSetFieldsCorrectly() {
            UUID userId = UUID.randomUUID();
            VerificationCode code = new VerificationCode("ABC123", 999999999999L, userId);

            assertThat(code.getCode()).isEqualTo("ABC123");
            assertThat(code.getExpiresAt()).isEqualTo(999999999999L);
            assertThat(code.getUserId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("Constructor with id")
    class FourArgConstructor {

        @Test
        @DisplayName("should set all fields including id")
        void shouldSetAllFields() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            VerificationCode code = new VerificationCode(id, "XYZ789", 888888888888L, userId);

            assertThat(code.getId()).isEqualTo(id);
            assertThat(code.getCode()).isEqualTo("XYZ789");
            assertThat(code.getExpiresAt()).isEqualTo(888888888888L);
            assertThat(code.getUserId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("isExpired()")
    class IsExpired {

        @Test
        @DisplayName("should return true when expiresAt is in the past")
        void shouldReturnTrueWhenExpired() {
            long pastTimestamp = Instant.now().toEpochMilli() - 60_000;
            VerificationCode code = new VerificationCode("CODE1", pastTimestamp, UUID.randomUUID());

            assertThat(code.isExpired()).isTrue();
        }

        @Test
        @DisplayName("should return false when expiresAt is in the future")
        void shouldReturnFalseWhenNotExpired() {
            long futureTimestamp = Instant.now().toEpochMilli() + 60_000;
            VerificationCode code = new VerificationCode("CODE2", futureTimestamp, UUID.randomUUID());

            assertThat(code.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should return true when expiresAt equals current time (boundary)")
        void shouldReturnTrueAtExactExpiry() {
            long now = Instant.now().toEpochMilli();
            VerificationCode code = new VerificationCode("CODE3", now, UUID.randomUUID());

            // The condition is >= so exactly now should be expired
            assertThat(code.isExpired()).isTrue();
        }
    }
}
