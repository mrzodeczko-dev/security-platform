package com.rzodeczko.domain.model;

import java.util.List;
import java.util.Map;

/** Domain representation of an HTTP request. */
public record GatewayRequest(
        String path,
        String method,
        Map<String, List<String>> headers,
        byte[] body
) {
}
