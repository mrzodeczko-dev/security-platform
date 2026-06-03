package com.app.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mfa.cache")
public record MfaCacheProperties(
        Long ttlSeconds,
        String keyPrefix
) {
}
