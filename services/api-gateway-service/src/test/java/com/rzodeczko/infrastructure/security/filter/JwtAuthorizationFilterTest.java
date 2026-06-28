package com.rzodeczko.infrastructure.security.filter;

import com.rzodeczko.application.port.out.TokenVerificationPort;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.model.TokenInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthorizationFilter")
class JwtAuthorizationFilterTest {

    private final TokenVerificationPort tokenVerificationPort = mock(TokenVerificationPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtAuthorizationFilter filter = new JwtAuthorizationFilter(tokenVerificationPort, objectMapper);
    private final FilterChain filterChain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("no Authorization header → pass through without authentication")
    void noAuthHeader() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/test");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("non-Bearer header → pass through without authentication")
    void nonBearerHeader() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("valid Bearer token → sets SecurityContext and continues chain")
    void validToken() throws ServletException, IOException {
        var userId = UUID.randomUUID();
        var tokenInfo = new TokenInfo(userId, "john", "ROLE_USER");
        when(tokenVerificationPort.verify("valid-jwt")).thenReturn(tokenInfo);

        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Authorization", "Bearer valid-jwt");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId.toString());
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_USER");

        @SuppressWarnings("unchecked")
        var details = (Map<String, String>) auth.getDetails();
        assertThat(details).containsEntry("username", "john");
    }

    @Test
    @DisplayName("valid token with null role → empty authorities")
    void validTokenNullRole() throws ServletException, IOException {
        var userId = UUID.randomUUID();
        var tokenInfo = new TokenInfo(userId, "john", null);
        when(tokenVerificationPort.verify("token")).thenReturn(tokenInfo);

        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("invalid token → 401 JSON error, no chain")
    void invalidToken() throws ServletException, IOException {
        when(tokenVerificationPort.verify("bad-jwt"))
                .thenThrow(new InvalidTokenException("expired"));

        var request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Authorization", "Bearer bad-jwt");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("expired");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
