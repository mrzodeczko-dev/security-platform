package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Role;
import com.rzodeczko.domain.model.User;
import com.rzodeczko.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    @DisplayName("toEntity maps all fields correctly for an enabled admin user with MFA")
    void toEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "admin", "admin@example.com", "hashed-password",
                Role.ADMIN, true, "JBSWY3DPEHPK3PXP", "otpauth://totp/app:admin?secret=JBSWY3DPEHPK3PXP");

        UserEntity entity = mapper.toEntity(user);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUsername()).isEqualTo("admin");
        assertThat(entity.getEmail()).isEqualTo("admin@example.com");
        assertThat(entity.getPassword()).isEqualTo("hashed-password");
        assertThat(entity.getRole()).isEqualTo(Role.ADMIN);
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getMfaSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(entity.getMfaQrUrl()).isEqualTo("otpauth://totp/app:admin?secret=JBSWY3DPEHPK3PXP");
    }

    @Test
    @DisplayName("toDomain maps all fields correctly")
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UserEntity entity = UserEntity.builder()
                .id(id).username("john").email("john@example.com").password("secret")
                .role(Role.USER).enabled(false).mfaSecret("MFA_SECRET").mfaQrUrl("https://qr.example.com")
                .build();

        User user = mapper.toDomain(entity);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("john");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getPassword()).isEqualTo("secret");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getMfaSecret()).isEqualTo("MFA_SECRET");
        assertThat(user.getMfaQrUrl()).isEqualTo("https://qr.example.com");
    }

    @Test
    @DisplayName("toEntity handles null MFA fields gracefully")
    void toEntity_handlesNullMfaFields() {
        User user = new User(UUID.randomUUID(), "user", "user@example.com", "pass",
                Role.USER, true, null, null);

        UserEntity entity = mapper.toEntity(user);

        assertThat(entity.getMfaSecret()).isNull();
        assertThat(entity.getMfaQrUrl()).isNull();
    }

    @Test
    @DisplayName("toDomain handles null MFA fields gracefully")
    void toDomain_handlesNullMfaFields() {
        UserEntity entity = UserEntity.builder()
                .id(UUID.randomUUID()).username("user").email("user@example.com").password("pass")
                .role(Role.USER).enabled(true).mfaSecret(null).mfaQrUrl(null)
                .build();

        User user = mapper.toDomain(entity);

        assertThat(user.getMfaSecret()).isNull();
        assertThat(user.getMfaQrUrl()).isNull();
    }

    @Test
    @DisplayName("Roundtrip toDomain(toEntity(user)) preserves all values")
    void roundtrip_preservesAllValues() {
        UUID id = UUID.randomUUID();
        User original = new User(id, "roundtrip", "roundtrip@example.com", "password123",
                Role.ADMIN, true, "SECRET123", "https://qr.example.com/roundtrip");

        User result = mapper.toDomain(mapper.toEntity(original));

        assertThat(result.getId()).isEqualTo(original.getId());
        assertThat(result.getUsername()).isEqualTo(original.getUsername());
        assertThat(result.getEmail()).isEqualTo(original.getEmail());
        assertThat(result.getPassword()).isEqualTo(original.getPassword());
        assertThat(result.getRole()).isEqualTo(original.getRole());
        assertThat(result.isEnabled()).isEqualTo(original.isEnabled());
        assertThat(result.getMfaSecret()).isEqualTo(original.getMfaSecret());
        assertThat(result.getMfaQrUrl()).isEqualTo(original.getMfaQrUrl());
    }
}
