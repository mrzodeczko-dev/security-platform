package com.rzodeczko.infrastructure.forwarding;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.domain.exception.DownstreamUnavailableException;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP adapter that forwards domain requests to downstream services.
 *
 * <p>Flow: GatewayRequest (domain) -> RestClient -> ResponseEntity -> GatewayResponse (domain).
 * The gateway forwards downstream status, headers and body to the client unchanged.
 * Raw byte arrays are used for payloads; no JSON parsing is performed here.
 *
 * <p>Each downstream target gets its own CircuitBreaker instance. When a downstream
 * fails repeatedly, the circuit opens and requests fail fast with 502 instead of
 * waiting for timeouts.
 *
 * <p>Errors: RestClientResponseException (4xx/5xx) are propagated 1:1. Network errors are
 * translated to DownstreamUnavailableException which maps to 502.
 */
@Component
@Slf4j
public class HttpForwardingAdapter implements ForwardingPort {

    private final RestClient restClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public HttpForwardingAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .recordExceptions(DownstreamUnavailableException.class)
                        .build()
        );
    }

    @Override
    public GatewayResponse forward(String targetBaseUrl, GatewayRequest request) {
        log.debug("Forwarding {} {} -> {}", request.method(), request.path(), targetBaseUrl);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(targetBaseUrl);

        return cb.executeSupplier(() -> doForward(targetBaseUrl, request));
    }

    private GatewayResponse doForward(String targetBaseUrl, GatewayRequest request) {
        try {
            // Normalize the path to prevent path traversal (e.g. /auth/../users/admin)
            String normalizedPath = URI.create(request.path()).normalize().getPath();
            if (normalizedPath.contains("../")) {
                throw new IllegalArgumentException("Path traversal detected: " + request.path());
            }
            String uri = request.queryString() != null
                    ? targetBaseUrl + normalizedPath + "?" + request.queryString()
                    : targetBaseUrl + normalizedPath;

            ResponseEntity<byte[]> response = restClient
                    .method(HttpMethod.valueOf(request.method()))
                    .uri(uri)
                    .headers(h -> request.headers().forEach(h::addAll))
                    .body(request.body() != null ? request.body() : new byte[0])
                    .retrieve()
                    .toEntity(byte[].class);

            Map<String, List<String>> responseHeaders = new HashMap<>();
            response.getHeaders().forEach(responseHeaders::put);

            return new GatewayResponse(
                    response.getStatusCode().value(),
                    responseHeaders,
                    response.getBody()
            );
        } catch (RestClientResponseException e) {
            log.warn("Downstream {} returned {}", targetBaseUrl + request.path(), e.getStatusCode());
            Map<String, List<String>> responseHeaders = new HashMap<>();
            if (e.getResponseHeaders() != null) {
                e.getResponseHeaders().forEach(responseHeaders::put);
            }

            return new GatewayResponse(
                    e.getStatusCode().value(),
                    responseHeaders,
                    e.getResponseBodyAsByteArray()
            );
        } catch (Exception e) {
            throw new DownstreamUnavailableException(targetBaseUrl, e.getMessage());
        }
    }
}
