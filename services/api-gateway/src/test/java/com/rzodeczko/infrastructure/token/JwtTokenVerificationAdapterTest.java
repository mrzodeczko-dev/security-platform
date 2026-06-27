package com.rzodeczko.infrastructure.token;

import com.rzodeczko.domain.exception.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenVerificationAdapterTest {

    private JwtTokenVerificationAdapter adapter;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        // 64-byte key for HS512
        byte[] keyBytes = new byte[64];
        java.util.Arrays.fill(keyBytes, (byte) 'A');
        secretKey = new SecretKeySpec(keyBytes, "HmacSHA512");
        adapter = new JwtTokenVerificationAdapter(secretKey);
    }

    private String buildToken(UUID userId, String username, String role, String type, Instant expiration) {
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(Date.from(Instant.now()))
                .signWith(secretKey);
        if (expiration != null) {
            builder.expiration(Date.from(expiration));
        }
        return builder.compact();
    }

    @Test
    @DisplayName("verifies valid access token and returns TokenInfo")
    void verifiesValidAccessToken() {
        var userId = UUID.randomUUID();
        var token = buildToken(userId, "john", "ROLE_USER", "access",
                Instant.now().plus(1, ChronoUnit.HOURS));

        var info = adapter.verify(token);

        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.username()).isEqualTo("john");
        assertThat(info.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("throws InvalidTokenException for expired token")
    void throwsForExpiredToken() {
        var token = buildToken(UUID.randomUUID(), "john", "ROLE_USER", "access",
                Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("throws InvalidTokenException for refresh token type")
    void throwsForRefreshTokenType() {
        var token = buildToken(UUID.randomUUID(), "john", "ROLE_USER", "refresh",
                Instant.now().plus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Expected access token");
    }

    @Test
    @DisplayName("throws InvalidTokenException for invalid signature")
    void throwsForInvalidSignature() {
        byte[] otherKeyBytes = new byte[64];
        java.util.Arrays.fill(otherKeyBytes, (byte) 'B');
        var otherKey = new SecretKeySpec(otherKeyBytes, "HmacSHA512");

        var token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "access")
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("throws InvalidTokenException for malformed token")
    void throwsForMalformedToken() {
        assertThatThrownBy(() -> adapter.verify("not.a.jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("handles token with null username and role")
    void handlesNullClaims() {
        var userId = UUID.randomUUID();
        var token = buildToken(userId, null, null, "access",
                Instant.now().plus(1, ChronoUnit.HOURS));

        var info = adapter.verify(token);

        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.username()).isNull();
        assertThat(info.role()).isNull();
    }
}
