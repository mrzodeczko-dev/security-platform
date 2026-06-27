package com.rzodeczko.application.dto;

import java.util.UUID;

public record MfaDataResultDto(
        UUID userId,
        String username,
        String role,
        String mfaSecret
) {
}
