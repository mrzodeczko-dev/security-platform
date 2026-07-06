package com.rzodeczko.application.command;

public record LogoutCommand(String refreshToken, boolean revokeAll) {

    public LogoutCommand(String refreshToken) {
        this(refreshToken, false);
    }
}
