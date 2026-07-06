package com.rzodeczko.infrastructure.security;

import com.rzodeczko.infrastructure.configuration.properties.InternalSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Rejects any request that does not carry a valid X-Internal-Secret header.
 *
 * <p>The auth service is never exposed publicly; all legitimate traffic arrives via the
 * API gateway, which attaches the shared internal secret. Actuator endpoints are exempt
 * because Kubernetes probes and Prometheus scraping cannot present the secret.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalRequestFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator";

    private final InternalSecurityProperties internalSecurityProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith(ACTUATOR_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        var providedSecret = request.getHeader(INTERNAL_SECRET_HEADER);

        boolean secretValid = providedSecret != null && MessageDigest.isEqual(
                providedSecret.getBytes(),
                internalSecurityProperties.secret().getBytes()
        );

        if (!secretValid) {
            log.warn(
                    "Unauthorized request to {} from IP={}. Missing or invalid X-Internal-Secret header.",
                    request.getRequestURI(),
                    request.getRemoteAddr()
            );
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", "Forbidden")));
            response.getWriter().flush();
            return;
        }
        filterChain.doFilter(request, response);
    }
}
