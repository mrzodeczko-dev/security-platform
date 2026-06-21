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
 * Extracts and verifies JWT from the Authorization header, populating the {@link SecurityContextHolder}.
 * Registered manually in the security filter chain (not a {@code @Component}) to stay under Spring Security's control.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthorizationFilter extends OncePerRequestFilter {

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

            // 3-arg constructor -> authenticated=true
            var auth = new UsernamePasswordAuthenticationToken(
                    tokenInfo.userId().toString(),
                    null,
                    tokenInfo.role() != null
                            ? List.of(new SimpleGrantedAuthority(tokenInfo.role()))
                            : List.of()
            );


            // Carry user identity for downstream X-User-* header injection
            auth.setDetails(Map.of(
                    "userId", tokenInfo.userId().toString(),
                    "username", tokenInfo.username() != null ? tokenInfo.username() : "",
                    "role", tokenInfo.role() != null ? tokenInfo.role() : ""
            ));

            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (InvalidTokenException e) {
            log.warn("JWT validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            // Prevent stale context leaking to subsequent requests on the same thread
            SecurityContextHolder.clearContext();
            writeError(response, HttpStatus.UNAUTHORIZED, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Writes a JSON error response directly - filters run before {@code @RestControllerAdvice}. */
    private void writeError(HttpServletResponse res, HttpStatus status, String msg) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(objectMapper.writeValueAsString(Map.of("error", msg)));
        res.getWriter().flush();
    }
}
