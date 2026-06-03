package com.rzodeczko.infrastructure.cache;


import com.rzodeczko.application.port.MfaCachePort;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.infrastructure.configuration.properties.MfaCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMfaCacheAdapter implements MfaCachePort {

    private final StringRedisTemplate stringRedisTemplate;
    private final MfaCacheProperties mfaCacheProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<MfaData> get(String username) {
        var key = buildKey(username);
        try {
            var json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                log.debug("MFA Redis cache miss for username {}", username);
                return Optional.empty();
            }

            log.debug("MFA Redis cache hit for username={}", username);
            return Optional.of(objectMapper.readValue(json, MfaData.class));
        } catch (Exception e) {
            // Blad deserializacji lub problem z Redis
            log.warn("MFA cache get failed for username={}, treating as miss: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String username, MfaData mfaData) {
        var key = buildKey(username);
        try {
            var json = objectMapper.writeValueAsString(mfaData);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(mfaCacheProperties.ttlSeconds()));
            log.debug("MFA data cached in Redis for username={}, TTL={}s", username, mfaCacheProperties.ttlSeconds());
        } catch (Exception e) {
            // Blad zapisu do Redis - logujemy
            log.warn("MFA cache put failed for username={}: {}", username, e.getMessage());
        }
    }

    @Override
    public void invalidate(String username) {
        var key = buildKey(username);
        Boolean deleted = stringRedisTemplate.delete(key);
        log.info("MFA cache invalidated for username={}, deleted={}", username, deleted);
    }

    private String buildKey(String username) {
        return mfaCacheProperties.keyPrefix() + username;
    }
}
