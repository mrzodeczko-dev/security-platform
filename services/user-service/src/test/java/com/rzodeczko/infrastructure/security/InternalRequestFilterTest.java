package com.rzodeczko.infrastructure.security;

import com.rzodeczko.infrastructure.configuration.properties.InternalSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalRequestFilterTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FilterChain filterChain;

    private InternalRequestFilter filter;

    private final InternalSecurityProperties properties = new InternalSecurityProperties("my-secret");

    @BeforeEach
    void setUp() {
        filter = new InternalRequestFilter(properties, objectMapper);
    }

    @Nested
    @DisplayName("actuator paths")
    class ActuatorPaths {

        @Test
        @DisplayName("should pass through to filterChain for actuator path without secret")
        void shouldPassThroughForActuatorPath() throws Exception {
            // given
            var request = new MockHttpServletRequest("GET", "/actuator/health");
            var response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("business paths")
    class BusinessPaths {

        @Test
        @DisplayName("should pass through with valid secret")
        void shouldPassThroughWithValidSecret() throws Exception {
            // given
            var request = new MockHttpServletRequest("POST", "/users");
            request.addHeader("X-Internal-Secret", "my-secret");
            var response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 403 without secret")
        void shouldReturn403WithoutSecret() throws Exception {
            // given
            var request = new MockHttpServletRequest("POST", "/users");
            var response = new MockHttpServletResponse();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Forbidden\"}");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(403);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("internal paths")
    class InternalPaths {

        @Test
        @DisplayName("should pass through with valid secret")
        void shouldPassThroughWithValidSecret() throws Exception {
            // given
            var request = new MockHttpServletRequest("GET", "/internal/users/check");
            request.addHeader("X-Internal-Secret", "my-secret");
            var response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 403 with invalid secret")
        void shouldReturn403WithInvalidSecret() throws Exception {
            // given
            var request = new MockHttpServletRequest("GET", "/internal/users/check");
            request.addHeader("X-Internal-Secret", "wrong-secret");
            var response = new MockHttpServletResponse();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Forbidden\"}");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Forbidden\"}");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should return 403 with no header")
        void shouldReturn403WithNoHeader() throws Exception {
            // given
            var request = new MockHttpServletRequest("GET", "/internal/users/check");
            var response = new MockHttpServletResponse();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Forbidden\"}");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentType()).isEqualTo("application/json");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should return 403 with null secret header")
        void shouldReturn403WithNullSecretHeader() throws Exception {
            // given
            var request = new MockHttpServletRequest("GET", "/internal/users/check");
            // explicitly not adding the header — getHeader returns null
            var response = new MockHttpServletResponse();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Forbidden\"}");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(403);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }
}
