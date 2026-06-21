package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.port.in.GatewayPort;
import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.domain.exception.RouteNotFoundException;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import com.rzodeczko.domain.model.RoutingTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class GatewayServiceImpl implements GatewayPort {

    // Headers that should not leak from downstream to the client
    private static final Set<String> STRIPPED_RESPONSE_HEADERS = Set.of(
            "server", "x-powered-by", "transfer-encoding", "connection",
            "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "upgrade"
    );

    private final ForwardingPort forwardingPort;
    private final RoutingTable routingTable;

    public GatewayServiceImpl(ForwardingPort forwardingPort, RoutingTable routingTable) {
        this.forwardingPort = forwardingPort;
        this.routingTable = routingTable;
    }

    /**
     * Accepts a domain request, resolves the target, filters headers, enriches with trusted user data
     * and forwards the request to the downstream service.
     */
    @Override
    public GatewayResponse handle(
            GatewayRequest request,
            String userId,
            String username,
            String userRole) {

        // Resolve route by first matching prefix from the routing table
        var targetBaseUrl = routingTable
                .resolveTarget(request.path())
                .orElseThrow(() -> new RouteNotFoundException(request.path()));

        // Build forwarded headers from a white-list to prevent client spoofing
        // Avoid forwarding Authorization, Host, Transfer-Encoding, etc.
        Map<String, List<String>> forwardedHeaders = new HashMap<>();

        // Cookie is intentionally excluded — forwarding all client cookies to downstream
        // services could leak session tokens or tracking cookies not meant for them.
        // If downstream needs specific cookies, add a dedicated cookie-forwarding mechanism.
        var safeHeaders = List.of("content-type", "accept", "cache-control");
        request.headers().forEach((name, values) -> {
            if (safeHeaders.contains(name.toLowerCase())) {
                forwardedHeaders.put(name, new ArrayList<>(values));
            }
        });

        // Reuse client-provided X-Request-Id or generate a new one for distributed tracing
        String requestId = Optional.ofNullable(request.headers().get("X-Request-Id"))
                .flatMap(values -> values.stream().findFirst())
                .orElse(UUID.randomUUID().toString());
        forwardedHeaders.put("X-Request-Id", List.of(requestId));

        // Add trusted X-User-* headers validated by the gateway (absent for public paths)
        if (userId != null) {
            forwardedHeaders.put("X-User-Id", List.of(userId));
        }

        if (username != null) {
            forwardedHeaders.put("X-User-Name", List.of(username));
        }

        if (userRole != null) {
            forwardedHeaders.put("X-User-Role", List.of(userRole));
        }

        // Create a forwarded GatewayRequest with filtered and enriched headers and delegate to forwarding port
        var forwardedRequest = new GatewayRequest(
                request.path(),
                request.method(),
                forwardedHeaders,
                request.body(),
                request.queryString()
        );

        var response = forwardingPort.forward(targetBaseUrl, forwardedRequest);

        // Strip hop-by-hop and stack-revealing headers, add X-Request-Id
        Map<String, List<String>> responseHeaders = new HashMap<>();
        response.headers().forEach((name, values) -> {
            if (!STRIPPED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                responseHeaders.put(name, values);
            }
        });
        responseHeaders.put("X-Request-Id", List.of(requestId));

        return new GatewayResponse(response.statusCode(), responseHeaders, response.body());
    }
}
