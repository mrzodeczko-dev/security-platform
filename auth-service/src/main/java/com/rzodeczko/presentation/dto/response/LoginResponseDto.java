package com.rzodeczko.presentation.dto.response;

public record LoginResponseDto(
        boolean mfaRequired,
        String mfaId,
        String usernameForMfa,
        AccessTokenResponseDto accessToken
) {
}
