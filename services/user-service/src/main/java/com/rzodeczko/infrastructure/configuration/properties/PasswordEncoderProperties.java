package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "password")
public record PasswordEncoderProperties(Encoder encoder) {
    public record Encoder(String type) {}
}
