package com.rzodeczko.presentation.dto.response;

public record LoginResponseDto(
        boolean mfaRequired,
        String usernameForMfa,
        AccessTokenResponseDto accessToken
) {
}
