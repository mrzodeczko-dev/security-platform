package com.rzodeczko.infrastructure.cache;

import com.rzodeczko.application.port.RefreshTokenPort;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRefreshTokenAdapter implements RefreshTokenPort {

    private static final String KEY_PREFIX = "refresh-token:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(String jti, UUID userId) {
        var key = KEY_PREFIX + jti;
        var ttl = Duration.ofMillis(jwtProperties.refreshTokenExpirationMs());
        stringRedisTemplate.opsForValue().set(key, userId.toString(), ttl);
        log.debug("Refresh token stored: jti={}, userId={}, TTL={}ms", jti, userId, jwtProperties.refreshTokenExpirationMs());
    }

    @Override
    public boolean exists(String jti) {
        var key = KEY_PREFIX + jti;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    @Override
    public void delete(String jti) {
        var key = KEY_PREFIX + jti;
        Boolean deleted = stringRedisTemplate.delete(key);
        log.debug("Refresh token deleted: jti={}, deleted={}", jti, deleted);
    }
}
