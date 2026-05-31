package com.rzodeczko.domain.model;

import java.time.Instant;
import java.util.UUID;

public class VerificationCode {
    private UUID id;
    private final String code;
    private final long expiresAt;
    private final UUID userId;

    public VerificationCode(String code, long expiresAt, UUID userId) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    public VerificationCode(UUID id, String code, long expiresAt, UUID userId) {
        this.id = id;
        this.code = code;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    public boolean isExpired() {
        return Instant.now().toEpochMilli() >= this.expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }
}
