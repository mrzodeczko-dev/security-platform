package com.rzodeczko.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Domain representation of an HTTP response returned by downstream services.
 *
 * <p>Converted by the infrastructure layer from ResponseEntity<byte[]> and
 * mapped to ResponseEntity<byte[]> by the presentation layer.
 *
 * <p>The gateway acts as a transparent proxy: statusCode, headers and body are
 * forwarded to the client without modification.
 */
public record GatewayResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body
) {
}
