package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        List<RouteDefinition> routes,
        List<String> publicPaths,
        CorsProperties cors,
        ForwardingProperties forwarding
) {
    public record RouteDefinition(String prefix, String target) {
    }

    public record CorsProperties(List<String> allowedOrigins) {
    }

    public record ForwardingProperties(Long connectTimeoutMs, Long readTimeoutMs) {
    }
}
