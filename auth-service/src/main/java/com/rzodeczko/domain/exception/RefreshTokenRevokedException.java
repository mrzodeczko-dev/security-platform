package com.rzodeczko.domain.exception;

public class RefreshTokenRevokedException extends RuntimeException {
    public RefreshTokenRevokedException() {
        super("Refresh token has been revoked or is not recognized.");
    }
}
