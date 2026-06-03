package com.rzodeczko.domain.exception;

public class MfaAuthorizationFailedException extends RuntimeException {
    public MfaAuthorizationFailedException() {
        super("MFA authorization failed: invalid or expired TOTP code");
    }
}
