package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GatewayPort;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

/**
 * REST controller that accepts all incoming HTTP requests, converts them to the
 * domain {@link com.rzodeczko.domain.model.GatewayRequest} and delegates routing
 * and forwarding to the application service.
 */
@RestController
@RequestMapping("/**")
@RequiredArgsConstructor
@Slf4j
public class GatewayController {
    private final GatewayPort gatewayService;

    /**
     * Handle any HTTP request, map it to a domain request and return downstream response.
     *
     * @param request incoming servlet request
     * @param authentication authentication set by the JWT filter, may be null
     * @return response from downstream service as ResponseEntity<byte[]>
     * @throws IOException on I/O errors reading the request body
     */
    @RequestMapping
    public ResponseEntity<byte[]> handle(
            HttpServletRequest request,
            Authentication authentication
    ) throws IOException {
        // Extract user data populated by JwtAuthorizationFilter
        String userId = null;
        String username = null;
        String userRole = null;

        if (authentication instanceof UsernamePasswordAuthenticationToken auth && auth.isAuthenticated()) {
            userId = (String) auth.getPrincipal();
            if (auth.getDetails() instanceof Map<?, ?> details) {
                username = (String) details.get("username");
                userRole = (String) details.get("role");
            }
        }

        // Build headers map from HttpServletRequest
        Map<String, List<String>> headers = new HashMap<>();
        var headersNames = request.getHeaderNames();
        while (headersNames.hasMoreElements()) {
            var name = headersNames.nextElement();
            var values = Collections.list(request.getHeaders(name));
            headers.put(name, values);
        }

        byte[] body = switch (request.getMethod()) {
            case "GET", "HEAD", "DELETE", "OPTIONS" -> new byte[0];
            default -> request.getInputStream().readAllBytes();
        };

        // Create domain GatewayRequest; subsequent layers work on domain types only
        var gatewayRequest = new GatewayRequest(
                request.getRequestURI(),
                request.getMethod(),
                headers,
                body,
                request.getQueryString()
        );

        log.debug("Gateway: {} {} userId={}", request.getMethod(), request.getRequestURI(), userId);

        // Delegate to service for routing, header filtering and forwarding
        GatewayResponse gatewayResponse = gatewayService.handle(
                gatewayRequest, userId, username, userRole);

        var responseHeaders = new HttpHeaders();
        gatewayResponse.headers().forEach(responseHeaders::addAll);

        return ResponseEntity
                .status(gatewayResponse.statusCode())
                .headers(responseHeaders)
                .body(gatewayResponse.body());
    }
}
