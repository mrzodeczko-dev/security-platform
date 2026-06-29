package com.rzodeczko.infrastructure.cache;

import com.rzodeczko.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRefreshTokenAdapterIT extends AbstractRedisIntegrationTest {

    @Autowired
    private RedisRefreshTokenAdapter refreshTokenAdapter;

    @Test
    void save_thenExists_returnsTrue() {
        var jti = UUID.randomUUID().toString();
        var userId = UUID.randomUUID();
        var familyId = UUID.randomUUID().toString();

        refreshTokenAdapter.save(jti, userId, familyId);

        assertThat(refreshTokenAdapter.exists(jti)).isTrue();
    }

    @Test
    void exists_missingKey_returnsFalse() {
        assertThat(refreshTokenAdapter.exists("nonexistent-jti")).isFalse();
    }

    @Test
    void delete_removesEntry() {
        var jti = UUID.randomUUID().toString();
        var familyId = UUID.randomUUID().toString();
        refreshTokenAdapter.save(jti, UUID.randomUUID(), familyId);

        refreshTokenAdapter.delete(jti);

        assertThat(refreshTokenAdapter.exists(jti)).isFalse();
    }

    @Test
    void save_multipleTokensForSameUser_allExistIndependently() {
        var userId = UUID.randomUUID();
        var familyId = UUID.randomUUID().toString();
        var jti1 = UUID.randomUUID().toString();
        var jti2 = UUID.randomUUID().toString();

        refreshTokenAdapter.save(jti1, userId, familyId);
        refreshTokenAdapter.save(jti2, userId, familyId);

        assertThat(refreshTokenAdapter.exists(jti1)).isTrue();
        assertThat(refreshTokenAdapter.exists(jti2)).isTrue();

        refreshTokenAdapter.delete(jti1);
        assertThat(refreshTokenAdapter.exists(jti1)).isFalse();
        assertThat(refreshTokenAdapter.exists(jti2)).isTrue();
    }

    @Test
    void deleteAllByFamilyId_removesAllTokensInFamily() {
        var userId = UUID.randomUUID();
        var familyId = UUID.randomUUID().toString();
        var jti1 = UUID.randomUUID().toString();
        var jti2 = UUID.randomUUID().toString();
        var jti3 = UUID.randomUUID().toString();

        refreshTokenAdapter.save(jti1, userId, familyId);
        refreshTokenAdapter.save(jti2, userId, familyId);
        refreshTokenAdapter.save(jti3, userId, familyId);

        refreshTokenAdapter.deleteAllByFamilyId(familyId);

        assertThat(refreshTokenAdapter.exists(jti1)).isFalse();
        assertThat(refreshTokenAdapter.exists(jti2)).isFalse();
        assertThat(refreshTokenAdapter.exists(jti3)).isFalse();
    }

    @Test
    void deleteAllByFamilyId_doesNotAffectOtherFamilies() {
        var userId = UUID.randomUUID();
        var familyA = UUID.randomUUID().toString();
        var familyB = UUID.randomUUID().toString();
        var jtiA = UUID.randomUUID().toString();
        var jtiB = UUID.randomUUID().toString();

        refreshTokenAdapter.save(jtiA, userId, familyA);
        refreshTokenAdapter.save(jtiB, userId, familyB);

        refreshTokenAdapter.deleteAllByFamilyId(familyA);

        assertThat(refreshTokenAdapter.exists(jtiA)).isFalse();
        assertThat(refreshTokenAdapter.exists(jtiB)).isTrue();
    }

    @Test
    void deleteAllByFamilyId_nonexistentFamily_doesNotThrow() {
        refreshTokenAdapter.deleteAllByFamilyId("nonexistent-family");
        // no exception
    }
}
