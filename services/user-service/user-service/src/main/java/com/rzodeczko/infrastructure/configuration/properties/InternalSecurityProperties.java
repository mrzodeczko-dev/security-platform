package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal")
public record InternalSecurityProperties(String secret) {
}
