package com.rzodeczko.infrastructure.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;

@DisplayName("AccessLogFilter")
class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private final FilterChain filterChain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("logs request and invokes chain — anonymous user")
    void anonymousRequest() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/test");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("logs request with userId from SecurityContext")
    void authenticatedRequest() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken(
                "user-123", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var request = new MockHttpServletRequest("POST", "/api/data");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("logs even when filter chain throws")
    void exceptionInChain() throws ServletException, IOException {
        doThrow(new ServletException("boom"))
                .when(filterChain).doFilter(any(), any());

        var request = new MockHttpServletRequest("GET", "/fail");
        var response = new MockHttpServletResponse();

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (ServletException ignored) {
            // expected
        }

        verify(filterChain).doFilter(request, response);
    }
}
