package com.rzodeczko.domain.exception;

/**
 * Thrown when no configured route matches the incoming request path.
 *
 * <p>Handled by GlobalExceptionHandler and translated to 404 Not Found.
 */
public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException(String path) {
        super("No route configured for path: " + path);
    }
}
