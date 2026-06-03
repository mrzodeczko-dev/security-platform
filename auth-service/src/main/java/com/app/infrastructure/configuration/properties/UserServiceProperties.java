package com.app.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user-service")
public record UserServiceProperties(
        String url,
        String connectTimeoutMs,
        String readTimeoutMs,
        String internalSecret

) {
}
