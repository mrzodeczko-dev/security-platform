package com.rzodeczko.infrastructure.adapter.dto;

import java.util.UUID;

public record VerifyCredentialsResponseDto(
        UUID userId,
        String username,
        String role,
        boolean mfaRequired
) {
}
