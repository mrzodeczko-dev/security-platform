package com.rzodeczko.domain.exception;

import java.util.UUID;


public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String value) {
        super("User not found: " + value);
    }

    public UserNotFoundException(UUID id) {
        super("User not found with id: " + id);
    }
}
