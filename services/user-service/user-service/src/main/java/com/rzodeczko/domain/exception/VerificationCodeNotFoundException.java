package com.rzodeczko.domain.exception;

public class VerificationCodeNotFoundException extends RuntimeException {
    public VerificationCodeNotFoundException() {
        super("Verification code not found");
    }
}
