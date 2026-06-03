package com.rzodeczko.application.dto;

public record LoginResultDto(
        boolean mfaRequired,
        String usernameForMfa,
        TokenPairDto tokens
) {
    public static LoginResultDto mfaRequired(String username) {
        return new LoginResultDto(true, username, null);
    }

    public static LoginResultDto withTokens(TokenPairDto tokens) {
        return new LoginResultDto(false, null, tokens);
    }
}
