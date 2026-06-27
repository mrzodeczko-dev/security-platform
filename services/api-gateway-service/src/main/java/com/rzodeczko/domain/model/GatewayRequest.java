package com.rzodeczko.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Domain representation of an incoming HTTP request.
 *
 * <p>Created by the presentation layer from HttpServletRequest so the
 * application layer is independent of servlet or Spring MVC APIs.
 *
 * <p>headers: Map<String, List<String>> — a header may have multiple values.
 * body: byte[] — raw bytes; the gateway does not parse request body content.
 * queryString: raw query string without the leading '?', may be null.
 */
public record GatewayRequest(
        String path,
        String method,
        Map<String, List<String>> headers,
        byte[] body,
        String queryString
) {
}
