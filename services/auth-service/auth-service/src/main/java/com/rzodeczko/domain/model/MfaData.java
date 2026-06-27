package com.rzodeczko.domain.model;

import java.util.UUID;

public record MfaData(
        UUID userId,
        String username,
        String role,
        String mfaSecret
) {
}
