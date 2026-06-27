package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.infrastructure.persistence.entity.VerificationCodeEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeMapperTest {

    private final VerificationCodeMapper mapper = new VerificationCodeMapper();

    @Test
    @DisplayName("toEntity maps all fields correctly")
    void toEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + 300_000;
        VerificationCode code = new VerificationCode(id, "123456", expiresAt, userId);

        VerificationCodeEntity entity = mapper.toEntity(code);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCode()).isEqualTo("123456");
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("toDomain maps all fields correctly")
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + 600_000;
        VerificationCodeEntity entity = VerificationCodeEntity.builder()
                .id(id).code("654321").expiresAt(expiresAt).userId(userId)
                .build();

        VerificationCode code = mapper.toDomain(entity);

        assertThat(code.getId()).isEqualTo(id);
        assertThat(code.getCode()).isEqualTo("654321");
        assertThat(code.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(code.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Roundtrip toDomain(toEntity(code)) preserves all values")
    void roundtrip_preservesAllValues() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + 900_000;
        VerificationCode original = new VerificationCode(id, "999888", expiresAt, userId);

        VerificationCode result = mapper.toDomain(mapper.toEntity(original));

        assertThat(result.getId()).isEqualTo(original.getId());
        assertThat(result.getCode()).isEqualTo(original.getCode());
        assertThat(result.getExpiresAt()).isEqualTo(original.getExpiresAt());
        assertThat(result.getUserId()).isEqualTo(original.getUserId());
    }
}
