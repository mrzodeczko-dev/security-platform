package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        List<RouteDefinition> routes,
        List<String> publicPaths,
        List<String> adminPaths,
        List<String> userPaths,
        CorsProperties cors,
        ForwardingProperties forwarding,
        RateLimitProperties rateLimit
) {
    public record RouteDefinition(String prefix, String target) {
    }

    public record CorsProperties(List<String> allowedOrigins) {
    }

    public record ForwardingProperties(Long connectTimeoutMs, Long readTimeoutMs) {
    }

    public record RateLimitProperties(
            boolean enabled,
            long requestsPerSecond,
            long burstCapacity,
            RedisProperties redis
    ) {
        public record RedisProperties(String host, int port, String password) {
        }
    }
}
