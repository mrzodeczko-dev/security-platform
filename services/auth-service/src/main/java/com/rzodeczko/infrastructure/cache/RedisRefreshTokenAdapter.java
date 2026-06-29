package com.rzodeczko.infrastructure.cache;

import com.rzodeczko.application.port.RefreshTokenPort;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRefreshTokenAdapter implements RefreshTokenPort {

    private static final String TOKEN_PREFIX = "refresh-token:";
    private static final String FAMILY_PREFIX = "refresh-family:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(String jti, UUID userId, String familyId) {
        var ttl = Duration.ofMillis(jwtProperties.refreshTokenExpirationMs());

        // Store token entry: refresh-token:{jti} → userId:familyId
        var tokenKey = TOKEN_PREFIX + jti;
        stringRedisTemplate.opsForValue().set(tokenKey, userId + ":" + familyId, ttl);

        // Add jti to family set: refresh-family:{familyId} → Set(jti)
        var familyKey = FAMILY_PREFIX + familyId;
        stringRedisTemplate.opsForSet().add(familyKey, jti);
        stringRedisTemplate.expire(familyKey, ttl);

        log.debug("Refresh token stored: jti={}, familyId={}, userId={}, TTL={}ms",
                jti, familyId, userId, jwtProperties.refreshTokenExpirationMs());
    }

    @Override
    public boolean exists(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_PREFIX + jti));
    }

    @Override
    public void delete(String jti) {
        var tokenKey = TOKEN_PREFIX + jti;

        // Read familyId before deleting so we can clean up the family set
        var value = stringRedisTemplate.opsForValue().get(tokenKey);
        Boolean deleted = stringRedisTemplate.delete(tokenKey);

        if (value != null && value.contains(":")) {
            var familyId = value.substring(value.indexOf(':') + 1);
            stringRedisTemplate.opsForSet().remove(FAMILY_PREFIX + familyId, jti);
        }

        log.debug("Refresh token deleted: jti={}, deleted={}", jti, deleted);
    }

    @Override
    public void deleteAllByFamilyId(String familyId) {
        var familyKey = FAMILY_PREFIX + familyId;
        Set<String> jtis = stringRedisTemplate.opsForSet().members(familyKey);

        if (jtis != null && !jtis.isEmpty()) {
            var tokenKeys = jtis.stream()
                    .map(jti -> TOKEN_PREFIX + jti)
                    .toList();
            stringRedisTemplate.delete(tokenKeys);
            log.warn("Token reuse detected — revoked {} tokens for familyId={}", jtis.size(), familyId);
        }

        stringRedisTemplate.delete(familyKey);
    }
}
