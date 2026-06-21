package com.rzodeczko.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;

public abstract class AbstractIntegrationTest {

    static final String JWT_SECRET_BASE64;
    static final SecretKey SECRET_KEY;

    static final GenericContainer<?> redis;

    static {
        byte[] keyBytes = new byte[64];
        Arrays.fill(keyBytes, (byte) 'T');
        JWT_SECRET_BASE64 = Base64.getEncoder().encodeToString(keyBytes);
        SECRET_KEY = new SecretKeySpec(keyBytes, "HmacSHA512");

        redis = new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
                .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET_BASE64);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
