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

        refreshTokenAdapter.save(jti, userId);

        assertThat(refreshTokenAdapter.exists(jti)).isTrue();
    }

    @Test
    void exists_missingKey_returnsFalse() {
        assertThat(refreshTokenAdapter.exists("nonexistent-jti")).isFalse();
    }

    @Test
    void delete_removesEntry() {
        var jti = UUID.randomUUID().toString();
        refreshTokenAdapter.save(jti, UUID.randomUUID());

        refreshTokenAdapter.delete(jti);

        assertThat(refreshTokenAdapter.exists(jti)).isFalse();
    }

    @Test
    void save_multipleTokensForSameUser_allExistIndependently() {
        var userId = UUID.randomUUID();
        var jti1 = UUID.randomUUID().toString();
        var jti2 = UUID.randomUUID().toString();

        refreshTokenAdapter.save(jti1, userId);
        refreshTokenAdapter.save(jti2, userId);

        assertThat(refreshTokenAdapter.exists(jti1)).isTrue();
        assertThat(refreshTokenAdapter.exists(jti2)).isTrue();

        refreshTokenAdapter.delete(jti1);
        assertThat(refreshTokenAdapter.exists(jti1)).isFalse();
        assertThat(refreshTokenAdapter.exists(jti2)).isTrue();
    }
}
