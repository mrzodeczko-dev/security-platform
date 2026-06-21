package com.rzodeczko.infrastructure.forwarding;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.domain.exception.DownstreamUnavailableException;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
 * <p>Errors: RestClientResponseException (4xx/5xx) are propagated 1:1. Network errors are
 * translated to DownstreamUnavailableException which maps to 502.
 */
@Component
@Slf4j
public class HttpForwardingAdapter implements ForwardingPort {

    private final RestClient restClient;

    public HttpForwardingAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public GatewayResponse forward(String targetBaseUrl, GatewayRequest request) {
        log.debug("Forwarding {} {} -> {}", request.method(), request.path(), targetBaseUrl);
        try {
            String uri = request.queryString() != null
                    ? targetBaseUrl + request.path() + "?" + request.queryString()
                    : targetBaseUrl + request.path();

            ResponseEntity<byte[]> response = restClient
                    .method(HttpMethod.valueOf(request.method()))
                    .uri(uri)
                    .headers(h -> request.headers().forEach(h::addAll))
                    .body(request.body() != null ? request.body() : new byte[0])
                    .retrieve()
                    .toEntity(byte[].class);

            // ResponseEntity -> GatewayResponse
            Map<String, List<String>> responseHeaders = new HashMap<>();
            response.getHeaders().forEach(responseHeaders::put);

            return new GatewayResponse(
                    response.getStatusCode().value(),
                    responseHeaders,
                    response.getBody()
            );
        } catch (RestClientResponseException e) {
            // Downstream zwrocil blad - propagujemy go 1:1
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
