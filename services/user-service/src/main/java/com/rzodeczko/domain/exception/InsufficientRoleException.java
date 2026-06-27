package com.rzodeczko.domain.exception;

public class InsufficientRoleException extends RuntimeException {
    public InsufficientRoleException(String requiredRole) {
        super("Insufficient role. Required: " + requiredRole);
    }
}
