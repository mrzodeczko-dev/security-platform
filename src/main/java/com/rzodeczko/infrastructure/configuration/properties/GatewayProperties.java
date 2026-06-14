package com.rzodeczko.infrastructure.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        List<RouteDefinition> routes,
        List<String> publicPaths,
        CorsProperties cors,
        ForwardingProperties forwarding
) {
    public record RouteDefinition(String prefix, String target) {
    }

    public record CorsProperties(List<String> allowedOrigins) {
    }

    public record ForwardingProperties(Long connectTimeoutMs, Long readTimeoutMs) {
    }

    /** Resolves the target base URL for the first route matching the request path prefix. */
    public Optional<String> resolveTarget(String requestPath) {
        return routes
                .stream()
                .filter(r -> requestPath.startsWith(r.prefix))
                .findFirst()
                .map(RouteDefinition::target);
    }

    /** Checks if the given method+path matches any public path pattern (format: "METHOD:/path"). */
    public boolean isPublicPath(String method, String path) {
        return publicPaths
                .stream()
                .anyMatch(pattern -> {
                    var parts = pattern.split(":", 2);
                    return parts.length == 2
                            && parts[0].equalsIgnoreCase(method)
                            && parts[1].equals(path);
                });
    }
}
