package com.rzodeczko.domain.exception;

public class UserNotActivatedException extends RuntimeException {
    public UserNotActivatedException(String username) {
        super("User is not activated: " + username);
    }
}
