package com.rzodeczko.infrastructure.security.filter;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Filter that enforces per-client rate limiting using Bucket4j with Redis-backed distributed buckets.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> bucketConfigSupplier;
    private final ObjectMapper objectMapper;
    private final Set<String> trustedProxies;

    public RateLimitFilter(
            ProxyManager<String> proxyManager,
            Supplier<BucketConfiguration> bucketConfigSupplier,
            ObjectMapper objectMapper,
            Set<String> trustedProxies) {
        this.proxyManager = proxyManager;
        this.bucketConfigSupplier = bucketConfigSupplier;
        this.objectMapper = objectMapper;
        this.trustedProxies = trustedProxies != null ? trustedProxies : Set.of();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        var key = resolveKey(request);

        BucketProxy bucket = proxyManager.builder()
                .build(key, bucketConfigSupplier);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
            log.warn("Rate limit exceeded for key={} on {} {}",
                    key, request.getMethod(), request.getRequestURI());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("error", "Too many requests")));
        }
    }

    private String resolveKey(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof String userId
                && !"anonymousUser".equals(userId)) {
            return "rl:user:" + userId;
        }
        return "rl:ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        var remoteAddr = request.getRemoteAddr();

        if (trustedProxies.contains(remoteAddr)) {
            var xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }

        return remoteAddr;
    }
}
