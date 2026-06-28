package com.rzodeczko.domain.model;

public record Email(String value) {

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        if (!value.contains("@")) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
    }
}
