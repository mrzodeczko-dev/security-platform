package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user-service")
public record UserServiceProperties(
        String url,
        Long connectTimeoutMs,
        Long readTimeoutMs,
        String internalSecret

) {
}
