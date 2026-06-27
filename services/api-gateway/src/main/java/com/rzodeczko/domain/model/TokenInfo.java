package com.rzodeczko.domain.model;

import java.util.UUID;

/**
 * Value object with claims extracted from a verified JWT token.
 *
 * <p>Fields are forwarded to downstream services via HTTP headers:
 * X-User-Id, X-User-Name, X-User-Role.
 */
public record TokenInfo(
        UUID userId,
        String username,
        String role
) {
}
