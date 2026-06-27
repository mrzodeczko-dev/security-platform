package com.rzodeczko.infrastructure.token;

import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.model.TokenType;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenAdapterTest {

    private static final String SECRET;
    private static final String OTHER_SECRET;

    static {
        try {
            SECRET = generateBase64Secret();
            OTHER_SECRET = generateBase64Secret();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final JwtTokenAdapter adapter = buildAdapter(SECRET, 900_000L, 604_800_000L);
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void generate_producesNonBlankDistinctTokens() {
        var pair = adapter.generate(userId, "john", "USER");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.accessToken()).isNotEqualTo(pair.refreshToken());
    }

    @Test
    void parseAccessToken_returnsCorrectClaims() {
        var pair = adapter.generate(userId, "john", "USER");

        var info = adapter.parse(pair.accessToken());

        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.username()).isEqualTo("john");
        assertThat(info.role()).isEqualTo("USER");
        assertThat(info.type()).isEqualTo(TokenType.ACCESS);
    }

    @Test
    void parseRefreshToken_returnsRefreshTypeWithJti() {
        var pair = adapter.generate(userId, "john", "USER");

        var info = adapter.parse(pair.refreshToken());

        assertThat(info.type()).isEqualTo(TokenType.REFRESH);
        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.username()).isEqualTo("john");
        assertThat(info.jti()).isNotBlank();
    }

    @Test
    void parseAccessToken_hasNoJti() {
        var pair = adapter.generate(userId, "john", "USER");

        var info = adapter.parse(pair.accessToken());

        assertThat(info.jti()).isNull();
    }

    @Test
    void generate_producesUniqueJtiPerRefreshToken() {
        var pair1 = adapter.generate(userId, "john", "USER");
        var pair2 = adapter.generate(userId, "john", "USER");

        var jti1 = adapter.parse(pair1.refreshToken()).jti();
        var jti2 = adapter.parse(pair2.refreshToken()).jti();

        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    void parse_garbageString_throwsInvalidTokenException() {
        assertThatThrownBy(() -> adapter.parse("not.a.valid.jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_wrongSignature_throwsInvalidTokenException() {
        var otherAdapter = buildAdapter(OTHER_SECRET, 900_000L, 604_800_000L);
        var pairSignedByOther = otherAdapter.generate(userId, "john", "USER");

        assertThatThrownBy(() -> adapter.parse(pairSignedByOther.accessToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_expiredToken_throwsInvalidTokenException() {
        var expiredAdapter = buildAdapter(SECRET, -1L, -1L);
        var pair = expiredAdapter.generate(userId, "john", "USER");

        assertThatThrownBy(() -> adapter.parse(pair.accessToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    private static String generateBase64Secret() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA512");
        keyGen.init(512);
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static JwtTokenAdapter buildAdapter(String secret, long accessMs, long refreshMs) {
        var props = new JwtProperties(secret, accessMs, refreshMs);
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        SecretKey key = new SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA512");
        return new JwtTokenAdapter(key, props);
    }
}