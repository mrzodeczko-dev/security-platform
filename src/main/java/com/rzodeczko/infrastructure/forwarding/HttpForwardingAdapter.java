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
 * Transparently forwards requests to downstream services via {@link RestClient}. Propagates responses as raw bytes.
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
            ResponseEntity<byte[]> response = restClient
                    .method(HttpMethod.valueOf(request.method()))
                    .uri(targetBaseUrl + request.path())
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
            log.error("!!! HttpForwardingAdapter forward error: msg=[{}], {}{}->{}", e.getMessage(), request.method(), request.path(), targetBaseUrl, e);
            throw new DownstreamUnavailableException(targetBaseUrl, e.getMessage());
        }
    }
}
