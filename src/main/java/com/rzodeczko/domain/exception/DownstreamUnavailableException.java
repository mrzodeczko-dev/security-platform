package com.rzodeczko.domain.exception;

/**
 * Thrown when a downstream service cannot be reached due to network error, timeout or DNS failure.
 *
 * <p>Responses with 4xx/5xx from downstream are not thrown here; they are propagated as responses.
 * This exception is handled by GlobalExceptionHandler and mapped to 502 Bad Gateway.
 */
public class DownstreamUnavailableException extends RuntimeException {
    public DownstreamUnavailableException(String service, String reason) {
        super("Service " + service + " unavailable: " + reason);
    }
}
