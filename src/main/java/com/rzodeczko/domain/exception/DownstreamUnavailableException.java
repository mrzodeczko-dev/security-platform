package com.rzodeczko.domain.exception;

/** Downstream unreachable (network error, timeout, DNS). HTTP 4xx/5xx are propagated as-is. Maps to 502. */
public class DownstreamUnavailableException extends RuntimeException {
    public DownstreamUnavailableException(String service, String reason) {
        super("Service " + service + " unavailable: " + reason);
    }
}
