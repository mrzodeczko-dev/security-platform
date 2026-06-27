package com.rzodeczko.application.dto;

import java.util.UUID;

public record UserCredentialsResultDto(
        UUID userId,
        String username,
        String role,
        boolean mfaRequired
) {
}
