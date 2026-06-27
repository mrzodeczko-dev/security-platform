package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mfa")
public record MfaProperties(String issuer) {
}
