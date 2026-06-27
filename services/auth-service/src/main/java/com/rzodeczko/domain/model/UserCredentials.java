package com.rzodeczko.domain.model;

import java.util.UUID;

public record UserCredentials(
        UUID userId,
        String username,
        String role,
        boolean mfaRequired
) {
}
