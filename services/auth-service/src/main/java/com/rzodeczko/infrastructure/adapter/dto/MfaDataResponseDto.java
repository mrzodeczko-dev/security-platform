package com.rzodeczko.infrastructure.adapter.dto;

import java.util.UUID;

public record MfaDataResponseDto(
        UUID userId,
        String username,
        String role,
        String mfaSecret
) {
}
