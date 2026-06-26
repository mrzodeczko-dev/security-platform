package com.rzodeczko.application.dto;

public record LoginResultDto(
        boolean mfaRequired,
        String mfaId,
        String usernameForMfa,
        TokenPairDto tokens
) {
    public static LoginResultDto mfaRequired(String mfaId, String username) {
        return new LoginResultDto(true, mfaId, username, null);
    }

    public static LoginResultDto withTokens(TokenPairDto tokens) {
        return new LoginResultDto(false, null, null, tokens);
    }
}
