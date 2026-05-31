package com.rzodeczko.domain.model;

import java.time.Instant;
import java.util.UUID;

public class VerificationCode {
    private UUID id;
    private final String code;

    // Unix timestamp w ms — kiedy kod traci ważność.
    // long (primitive) zamiast Long (wrapper) — brak NPE risk.
    private final long expiresAt;

    // Referencja do User przez UUID — nie przez obiekt.
    // Eliminuje coupling między VerificationCode a User w warstwie domeny.
    private final UUID userId;

    // Konstruktor dla nowych kodow - id null (przed insert do bazy danych)
    public VerificationCode(String code, long expiresAt, UUID userId) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    // Konstruktor rekonstrukcji z bazy
    public VerificationCode(UUID id, String code, long expiresAt, UUID userId) {
        this.id = id;
        this.code = code;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    // isExpired() — logika biznesowa "kod wygasł" należy do domeny.
    // NIE do kontrolera, NIE do serwisu infrastrukturalnego.
    // Instant.now().toEpochMilli() — milisekundowa precyzja, UTC.
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
