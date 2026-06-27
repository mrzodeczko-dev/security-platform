package com.rzodeczko.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User domain model")
class UserTest {

    @Nested
    @DisplayName("Constructor with 4 arguments")
    class FourArgConstructor {

        @Test
        @DisplayName("should set username, email, password, and role correctly")
        void shouldSetFieldsCorrectly() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            assertThat(user.getUsername()).isEqualTo("john");
            assertThat(user.getEmail()).isEqualTo("john@example.com");
            assertThat(user.getPassword()).isEqualTo("encoded123");
            assertThat(user.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("should have enabled set to false by default")
        void shouldBeDisabledByDefault() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should have id set to null")
        void shouldHaveNullId() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            assertThat(user.getId()).isNull();
        }
    }

    @Nested
    @DisplayName("Full constructor")
    class FullConstructor {

        @Test
        @DisplayName("should set all fields including id, enabled, mfaSecret, and mfaQrUrl")
        void shouldSetAllFields() {
            UUID id = UUID.randomUUID();
            User user = new User(id, "jane", "jane@example.com", "pass456",
                    Role.ADMIN, true, "secret123", "https://qr.example.com/code");

            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getUsername()).isEqualTo("jane");
            assertThat(user.getEmail()).isEqualTo("jane@example.com");
            assertThat(user.getPassword()).isEqualTo("pass456");
            assertThat(user.getRole()).isEqualTo(Role.ADMIN);
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.getMfaSecret()).isEqualTo("secret123");
            assertThat(user.getMfaQrUrl()).isEqualTo("https://qr.example.com/code");
        }
    }

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        @DisplayName("should set enabled to true")
        void shouldEnableUser() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            user.activate();

            assertThat(user.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updatePassword()")
    class UpdatePassword {

        @Test
        @DisplayName("should change the password to the new encoded value")
        void shouldChangePassword() {
            User user = new User("john", "john@example.com", "oldPassword", Role.USER);

            user.updatePassword("newEncodedPassword");

            assertThat(user.getPassword()).isEqualTo("newEncodedPassword");
        }
    }

    @Nested
    @DisplayName("enableMfa()")
    class EnableMfa {

        @Test
        @DisplayName("should set mfaSecret and mfaQrUrl")
        void shouldSetMfaFields() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            user.enableMfa("totp-secret", "https://qr.example.com/totp");

            assertThat(user.getMfaSecret()).isEqualTo("totp-secret");
            assertThat(user.getMfaQrUrl()).isEqualTo("https://qr.example.com/totp");
        }
    }

    @Nested
    @DisplayName("hasMfaActive()")
    class HasMfaActive {

        @Test
        @DisplayName("should return false when mfaQrUrl is null")
        void shouldReturnFalseWhenQrUrlIsNull() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            assertThat(user.hasMfaActive()).isFalse();
        }

        @Test
        @DisplayName("should return true when mfaQrUrl is set")
        void shouldReturnTrueWhenQrUrlIsSet() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            user.enableMfa("secret", "https://qr.example.com/code");

            assertThat(user.hasMfaActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("changeRole()")
    class ChangeRole {

        @Test
        @DisplayName("should change role to the new value")
        void shouldChangeRole() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            user.changeRole(Role.ADMIN);

            assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        }
    }

    @Nested
    @DisplayName("isAdmin()")
    class IsAdmin {

        @Test
        @DisplayName("should return true when role is ADMIN")
        void shouldReturnTrueForAdmin() {
            User user = new User("john", "john@example.com", "encoded123", Role.ADMIN);

            assertThat(user.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("should return false when role is USER")
        void shouldReturnFalseForUser() {
            User user = new User("john", "john@example.com", "encoded123", Role.USER);

            assertThat(user.isAdmin()).isFalse();
        }
    }
}
