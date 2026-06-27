package com.rzodeczko.presentation.dto.response;

import java.util.UUID;

public record MfaDataResponseDto(
        UUID userId,
        String username,
        String role,
        String mfaSecret
) {
}
