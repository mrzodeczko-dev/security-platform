package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.application.service.GatewayService;
import com.rzodeczko.domain.exception.RouteNotFoundException;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import com.rzodeczko.domain.model.RoutingTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GatewayServiceImpl implements GatewayService {

    private final ForwardingPort forwardingPort;
    private final RoutingTable routingTable;

    public GatewayServiceImpl(ForwardingPort forwardingPort, RoutingTable routingTable) {
        this.forwardingPort = forwardingPort;
        this.routingTable = routingTable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayResponse handle(
            GatewayRequest request,
            String userId,
            String username,
            String userRole) {

        var targetBaseUrl = routingTable
                .resolveTarget(request.path())
                .orElseThrow(() -> new RouteNotFoundException(request.path()));

        // Whitelist-only header forwarding - prevents client-injected X-User-* spoofing
        Map<String, List<String>> forwardedHeaders = new HashMap<>();

        var safeHeaders = List.of("content-type", "accept", "cache-control", "cookie");
        request.headers().forEach((name, values) -> {
            if (safeHeaders.contains(name.toLowerCase())) {
                forwardedHeaders.put(name, new ArrayList<>(values));
            }
        });

        // Gateway-trusted identity headers (null on public/unauthenticated paths)
        if (userId != null) {
            forwardedHeaders.put("X-User-Id", List.of(userId));
        }

        if (username != null) {
            forwardedHeaders.put("X-User-Name", List.of(username));
        }

        if (userRole != null) {
            forwardedHeaders.put("X-User-Role", List.of(userRole));
        }

        var forwardedRequest = new GatewayRequest(
                request.path(),
                request.method(),
                forwardedHeaders,
                request.body()
        );

        return forwardingPort.forward(targetBaseUrl, forwardedRequest);
    }
}
