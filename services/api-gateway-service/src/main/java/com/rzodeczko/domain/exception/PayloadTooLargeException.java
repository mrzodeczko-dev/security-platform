package com.rzodeczko.domain.exception;

/**
 * Thrown when the request body exceeds the configured maximum size.
 *
 * <p>Handled by GlobalExceptionHandler and translated to 413 Payload Too Large.
 */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(long maxBytes) {
        super("Request body exceeds maximum allowed size of %d bytes".formatted(maxBytes));
    }
}
