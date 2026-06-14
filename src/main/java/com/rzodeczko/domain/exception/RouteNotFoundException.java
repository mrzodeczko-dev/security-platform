package com.rzodeczko.domain.exception;

/** No configured route matches the request path. Maps to 404. */
public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException(String path) {
        super("No route configured for path: " + path);
    }
}
