package com.rzodeczko.domain.model;

public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static TokenType of(String value) {
        return switch (value) {
            case "access" -> TokenType.ACCESS;
            case "refresh" -> TokenType.REFRESH;
            default -> throw new IllegalArgumentException("Unknown token type: " + value);
        };
    }
}
