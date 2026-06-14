package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.service.GatewayService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/**")
@RequiredArgsConstructor
@Slf4j
public class GatewayController {
    private final GatewayService gatewayService;

    /** Catches all requests, translates to domain model, delegates to gateway service and returns the downstream response. */
    @RequestMapping
    public ResponseEntity<byte[]> handle(
            HttpServletRequest request,
            Authentication authentication
    ) throws IOException {
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

        Map<String, List<String>> headers = new HashMap<>();
        var headersNames = request.getHeaderNames();
        while (headersNames.hasMoreElements()) {
            var name = headersNames.nextElement();
            headers
                    .computeIfAbsent(name, k -> new ArrayList<>())
                    .add(request.getHeader(name));
        }

        byte[] body = request.getInputStream().readAllBytes();

        var gatewayRequest = new GatewayRequest(
                request.getRequestURI(),
                request.getMethod(),
                headers,
                body
        );

        log.debug("Gateway: {} {} userId={}", request.getMethod(), request.getRequestURI(), userId);

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
