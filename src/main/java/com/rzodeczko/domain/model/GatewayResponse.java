package com.rzodeczko.domain.model;

import java.util.List;
import java.util.Map;

/** Domain representation of an HTTP response. */
public record GatewayResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body
) {
}
