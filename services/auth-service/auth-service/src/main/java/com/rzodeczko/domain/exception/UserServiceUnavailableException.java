package com.rzodeczko.domain.exception;

public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException(String reason) {
        super("User service unavailable: " + reason);
    }
}
