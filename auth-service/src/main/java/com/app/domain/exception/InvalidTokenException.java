package com.app.domain.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String reason) {
        super("Invalid token: " + reason);
    }
}
