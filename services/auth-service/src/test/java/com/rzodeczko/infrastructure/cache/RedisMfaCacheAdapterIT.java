package com.rzodeczko.infrastructure.cache;

import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMfaCacheAdapterIT extends AbstractRedisIntegrationTest {

    @Autowired
    private RedisMfaCacheAdapter cache;

    @BeforeEach
    void cleanUp() {
        cache.invalidate("john");
        cache.invalidate("alice");
        cache.invalidate("bob");
    }

    @Test
    void put_thenGet_returnsStoredValue() {
        var mfaData = new MfaData(UUID.randomUUID(), "john", "USER", "TOTP_SECRET_123");

        cache.put("john", mfaData);

        var result = cache.get("john");
        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("john");
        assertThat(result.get().mfaSecret()).isEqualTo("TOTP_SECRET_123");
        assertThat(result.get().role()).isEqualTo("USER");
    }

    @Test
    void get_missingKey_returnsEmpty() {
        var result = cache.get("nonexistent-user-xyz");

        assertThat(result).isEmpty();
    }

    @Test
    void invalidate_removesEntry() {
        cache.put("alice", new MfaData(UUID.randomUUID(), "alice", "USER", "SECRET"));

        cache.invalidate("alice");

        assertThat(cache.get("alice")).isEmpty();
    }

    @Test
    void put_overwritesExistingEntry() {
        var first = new MfaData(UUID.randomUUID(), "bob", "USER", "SECRET_V1");
        var second = new MfaData(UUID.randomUUID(), "bob", "ADMIN", "SECRET_V2");

        cache.put("bob", first);
        cache.put("bob", second);

        var result = cache.get("bob");
        assertThat(result).isPresent();
        assertThat(result.get().mfaSecret()).isEqualTo("SECRET_V2");
        assertThat(result.get().role()).isEqualTo("ADMIN");
    }

    @Test
    void get_preservesUuid() {
        var id = UUID.randomUUID();
        cache.put("john", new MfaData(id, "john", "USER", "SECRET"));

        var result = cache.get("john");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(id);
    }

    // --- MFA Session (mfaId binding) ---

    @Test
    void storeMfaSession_thenGetMfaSession_returnsUsername() {
        cache.storeMfaSession("mfa-123", "john");

        var result = cache.getMfaSession("mfa-123");

        assertThat(result).isPresent().contains("john");
    }

    @Test
    void getMfaSession_missingKey_returnsEmpty() {
        assertThat(cache.getMfaSession("nonexistent-mfa")).isEmpty();
    }

    @Test
    void deleteMfaSession_removesEntry() {
        cache.storeMfaSession("mfa-456", "alice");

        cache.deleteMfaSession("mfa-456");

        assertThat(cache.getMfaSession("mfa-456")).isEmpty();
    }
}
