package com.rzodeczko.domain.model;

import java.util.UUID;

/** Decoded JWT claims after signature verification. */
public record TokenInfo(
        UUID userId,
        String username,
        String role
) {
}
