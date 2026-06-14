package com.rzodeczko.domain.exception;

/** Thrown when JWT validation fails (bad signature, expired, wrong type, etc.). */
public class InvalidTokenException extends RuntimeException{
    public InvalidTokenException(String reason){
        super("Invalid token: " + reason);
    }
}
