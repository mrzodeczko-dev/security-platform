package com.rzodeczko.domain.exception;

public class MfaAlreadyActivatedException extends RuntimeException {
    public MfaAlreadyActivatedException() {
        super("MFA already activated");
    }
}
