package com.rzodeczko.infrastructure.token;

import com.rzodeczko.application.port.out.TokenVerificationPort;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.model.TokenInfo;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenVerificationAdapter implements TokenVerificationPort {
    private final SecretKey secretKey;

    @Override
    public TokenInfo verify(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            var type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                throw new InvalidTokenException("Expected access token, got: " + type);
            }

            return new TokenInfo(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class)
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(e.getMessage());
        }
    }
}
