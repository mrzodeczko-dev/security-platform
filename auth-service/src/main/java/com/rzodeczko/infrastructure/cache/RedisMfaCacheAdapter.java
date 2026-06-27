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

    @Override
    public void storeMfaSession(String mfaId, String username) {
        var key = "mfa-session:" + mfaId;
        try {
            stringRedisTemplate.opsForValue().set(key, username, Duration.ofSeconds(mfaCacheProperties.ttlSeconds()));
            log.debug("MFA session stored in Redis: mfaId={}, username={}, TTL={}s", mfaId, username, mfaCacheProperties.ttlSeconds());
        } catch (Exception e) {
            log.warn("MFA session store failed for mfaId={}: {}", mfaId, e.getMessage());
        }
    }

    @Override
    public Optional<String> getMfaSession(String mfaId) {
        var key = "mfa-session:" + mfaId;
        try {
            var username = stringRedisTemplate.opsForValue().get(key);
            if (username == null) {
                log.debug("MFA session not found for mfaId={}", mfaId);
                return Optional.empty();
            }
            return Optional.of(username);
        } catch (Exception e) {
            log.warn("MFA session get failed for mfaId={}: {}", mfaId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteMfaSession(String mfaId) {
        var key = "mfa-session:" + mfaId;
        Boolean deleted = stringRedisTemplate.delete(key);
        log.debug("MFA session deleted for mfaId={}, deleted={}", mfaId, deleted);
    }

    private String buildKey(String username) {
        return mfaCacheProperties.keyPrefix() + username;
    }
}
