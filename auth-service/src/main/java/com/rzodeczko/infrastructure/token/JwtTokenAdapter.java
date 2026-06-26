package com.rzodeczko.infrastructure.token;

import com.rzodeczko.application.dto.TokenPairDto;
import com.rzodeczko.application.port.TokenPort;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.model.TokenInfo;
import com.rzodeczko.domain.model.TokenType;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenAdapter implements TokenPort {

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    @Override
    public TokenPairDto generate(UUID userId, String username, String role) {
        var now = new Date();
        var refreshJti = UUID.randomUUID().toString();
        return new TokenPairDto(
                buildToken(
                        userId,
                        username,
                        role,
                        now,
                        new Date(now.getTime() + jwtProperties.accessTokenExpirationMs()),
                        TokenType.ACCESS,
                        null
                ),
                buildToken(
                        userId,
                        username,
                        role,
                        now,
                        new Date(now.getTime() + jwtProperties.refreshTokenExpirationMs()),
                        TokenType.REFRESH,
                        refreshJti
                )
        );
    }

    @Override
    public TokenInfo parse(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new TokenInfo(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class),
                    TokenType.of(claims.get("type", String.class)),
                    claims.getId()
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(e.getMessage());
        }
    }

    private String buildToken(
            UUID userId,
            String username,
            String role,
            Date issuedAt,
            Date expiration,
            TokenType type,
            String jti
    ) {
        var builder = Jwts
                .builder()
                .header().add("typ", "JWT")
                .and()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .issuer("auth-service")
                .claims(Map.of(
                        "type", type.toString(),
                        "username", username,
                        "role", role
                ))
                .signWith(secretKey);

        if (jti != null) {
            builder.id(jti);
        }

        return builder.compact();
    }
}
