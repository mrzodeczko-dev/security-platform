package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user-activation")
public record UserActivationProperties(
        Long expirationMs,
        Integer codeDigits
) {
}
