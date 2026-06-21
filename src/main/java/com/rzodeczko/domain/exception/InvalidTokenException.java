package com.rzodeczko.domain.exception;

/**
 * Thrown when a JWT is invalid: bad signature, expired, wrong format or wrong token type.
 *
 * <p>Raised by the token verification adapter and handled by JwtAuthorizationFilter.
 */
public class InvalidTokenException extends RuntimeException{
    public InvalidTokenException(String reason){
        super("Invalid token: " + reason);
    }
}
