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
import java.util.HashMap;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenAdapter implements TokenPort {

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    @Override
    public TokenPairDto generate(UUID userId, String username, String role, String familyId) {
        var now = new Date();
        var accessJti = UUID.randomUUID().toString();
        var refreshJti = UUID.randomUUID().toString();
        return new TokenPairDto(
                buildToken(
                        userId, username, role, now,
                        new Date(now.getTime() + jwtProperties.accessTokenExpirationMs()),
                        TokenType.ACCESS, accessJti, null
                ),
                buildToken(
                        userId, username, role, now,
                        new Date(now.getTime() + jwtProperties.refreshTokenExpirationMs()),
                        TokenType.REFRESH, refreshJti, familyId
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
                    claims.getId(),
                    claims.get("familyId", String.class)
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
            String jti,
            String familyId
    ) {
        var claims = new HashMap<String, Object>();
        claims.put("type", type.toString());
        claims.put("username", username);
        claims.put("role", role);
        if (familyId != null) {
            claims.put("familyId", familyId);
        }

        var builder = Jwts
                .builder()
                .header().add("typ", "JWT")
                .and()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .issuer("auth-service")
                .claims(claims)
                .signWith(secretKey);

        if (jti != null) {
            builder.id(jti);
        }

        return builder.compact();
    }
}
