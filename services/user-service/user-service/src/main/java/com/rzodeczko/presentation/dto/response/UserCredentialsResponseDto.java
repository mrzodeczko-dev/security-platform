package com.rzodeczko.presentation.dto.response;

import java.util.UUID;

public record UserCredentialsResponseDto(
        UUID userId,
        String username,
        String role,
        boolean mfaRequired
) {
}
