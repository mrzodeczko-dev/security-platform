package com.rzodeczko.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SuppressWarnings("resource")
public abstract class AbstractRedisIntegrationTest {

    static final GenericContainer<?> redis;

    static {
        System.setProperty("api.version", "1.43");
        redis = new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
                .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "");
    }
}
