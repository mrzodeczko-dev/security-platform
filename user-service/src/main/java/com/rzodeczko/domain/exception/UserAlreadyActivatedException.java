package com.rzodeczko.domain.exception;

public class UserAlreadyActivatedException extends RuntimeException {
    public UserAlreadyActivatedException(String username) {
        super("User already activated: " + username);
    }
}
