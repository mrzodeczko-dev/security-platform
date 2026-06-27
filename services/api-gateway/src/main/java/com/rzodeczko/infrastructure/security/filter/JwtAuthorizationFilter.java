package com.rzodeczko.infrastructure.security.filter;

import com.rzodeczko.application.port.out.TokenVerificationPort;
import com.rzodeczko.domain.exception.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Filter that verifies JWT access tokens and populates SecurityContext with a principal and authorities.
 *
 * <p>Implemented as OncePerRequestFilter to guarantee single execution per request in complex dispatch scenarios
 * (forward, include, error). The filter is intentionally not a Spring component to avoid automatic global
 * registration; it is registered explicitly in the SecurityFilterChain so its position relative to other security
 * filters is controlled.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    // Authorization header must start with "Bearer " followed by the token
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenVerificationPort tokenVerificationPort;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        var header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var token = header.substring(BEARER_PREFIX.length());
            var tokenInfo = tokenVerificationPort.verify(token);

            // Build an authenticated Authentication using the 3-arg constructor so Spring treats it as authenticated
            // principal = userId as String; credentials = null since verification already occurred; authorities = role
            var auth = new UsernamePasswordAuthenticationToken(
                    tokenInfo.userId().toString(),
                    null,
                    tokenInfo.role() != null
                            ? List.of(new SimpleGrantedAuthority(tokenInfo.role()))
                            : List.of()
            );


            // Use details to carry additional user data needed by the controller and downstream services
            // Downstream services rely on these headers rather than re-verifying the JWT
            auth.setDetails(Map.of(
                    "userId", tokenInfo.userId().toString(),
                    "username", tokenInfo.username() != null ? tokenInfo.username() : "",
                    "role", tokenInfo.role() != null ? tokenInfo.role() : ""
            ));

            // Set Authentication in SecurityContext so downstream security checks see an authenticated principal
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (InvalidTokenException e) {
            log.warn("JWT validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            // Clear SecurityContext to avoid leaking authentication across thread reuse and return 401 JSON
            SecurityContextHolder.clearContext();
            writeError(response, HttpStatus.UNAUTHORIZED, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Write an error response directly from the filter.
     *
     * <p>GlobalExceptionHandler annotated with @RestControllerAdvice only handles exceptions thrown from controllers.
     * Exceptions thrown inside a filter do not reach that handler. Filters must thus write the HttpServletResponse
     * directly to control the returned status and body.
     */
    private void writeError(HttpServletResponse res, HttpStatus status, String msg) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(objectMapper.writeValueAsString(Map.of("error", msg)));
        res.getWriter().flush();
    }
}
