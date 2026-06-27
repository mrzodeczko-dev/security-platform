package com.rzodeczko.domain.model;

import java.util.List;
import java.util.Optional;

public record RoutingTable(List<Route> routes) {
    public record Route(String prefix, String target) {}

    public Optional<String> resolveTarget(String requestPath) {
        return routes
                .stream()
                .filter(r -> requestPath.startsWith(r.prefix()))
                .findFirst()
                .map(Route::target);
    }
}
