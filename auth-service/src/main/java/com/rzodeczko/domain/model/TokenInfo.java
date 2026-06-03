package com.rzodeczko.domain.model;

import java.util.UUID;

public record TokenInfo(
        UUID userId,
        String username,
        String role,
        TokenType type
) {
}
