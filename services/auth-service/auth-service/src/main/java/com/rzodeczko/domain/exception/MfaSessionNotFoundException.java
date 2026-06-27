package com.rzodeczko.domain.exception;

public class MfaSessionNotFoundException extends RuntimeException {
    public MfaSessionNotFoundException() {
        super("MFA session not found or expired. Please login again.");
    }
}
