package com.rzodeczko.domain.model;

import java.time.Instant;
import java.util.UUID;

public class VerificationCode {
    private UUID id;
    private final String code;

    // Unix timestamp in ms — when code expires.
    // long (primitive) instead of Long (wrapper) — no NPE risk.
    private final long expiresAt;

    // Reference to User via UUID — not via object.
    // Eliminates coupling between VerificationCode and User in domain layer.
    private final UUID userId;

    // Constructor for new codes - id null (before insert to database)
    public VerificationCode(String code, long expiresAt, UUID userId) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    // Reconstruction constructor from database
    public VerificationCode(UUID id, String code, long expiresAt, UUID userId) {
        this.id = id;
        this.code = code;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    // isExpired() — business logic "code expired" belongs to domain.
    // NOT to controller, NOT to infrastructure service.
    // Instant.now().toEpochMilli() — millisecond precision, UTC.
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
