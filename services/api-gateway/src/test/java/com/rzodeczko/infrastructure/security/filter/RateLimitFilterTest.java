package com.rzodeczko.infrastructure.security.filter;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ProxyManager<String> proxyManager;
    @Mock
    private RemoteBucketBuilder<String> bucketBuilder;
    @Mock
    private BucketProxy bucketProxy;
    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", "/test");
        request.setRemoteAddr("192.168.1.100");
        response = new MockHttpServletResponse();

        lenient().when(proxyManager.builder()).thenReturn(bucketBuilder);
        lenient().when(bucketBuilder.build(any(String.class), any(Supplier.class))).thenReturn(bucketProxy);
    }

    @Nested
    @DisplayName("Rate limit enforcement")
    class Enforcement {

        @Test
        @DisplayName("allows request when bucket has tokens")
        void allowsWhenTokensAvailable() throws Exception {
            var probe = mock(ConsumptionProbe.class);
            when(probe.isConsumed()).thenReturn(true);
            when(probe.getRemainingTokens()).thenReturn(19L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            var filter = new RateLimitFilter(proxyManager, () -> null, objectMapper, Set.of());
            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("19");
        }

        @Test
        @DisplayName("returns 429 when bucket is empty")
        void returns429WhenEmpty() throws Exception {
            var probe = mock(ConsumptionProbe.class);
            when(probe.isConsumed()).thenReturn(false);
            when(probe.getNanosToWaitForRefill()).thenReturn(2_000_000_000L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            var filter = new RateLimitFilter(proxyManager, () -> null, objectMapper, Set.of());
            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("3");
            assertThat(response.getContentAsString()).contains("Too many requests");
        }
    }

    @Nested
    @DisplayName("Key resolution")
    class KeyResolution {

        @Test
        @DisplayName("uses userId key for authenticated user")
        void usesUserIdForAuthenticated() throws Exception {
            var auth = new UsernamePasswordAuthenticationToken(
                    "user-42", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            var probe = mock(ConsumptionProbe.class);
            when(probe.isConsumed()).thenReturn(true);
            when(probe.getRemainingTokens()).thenReturn(10L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            var filter = new RateLimitFilter(proxyManager, () -> null, objectMapper, Set.of());
            filter.doFilterInternal(request, response, filterChain);

            verify(bucketBuilder).build(eq("rl:user:user-42"), any(Supplier.class));
        }

        @Test
        @DisplayName("uses IP key for anonymous request")
        void usesIpForAnonymous() throws Exception {
            var probe = mock(ConsumptionProbe.class);
            when(probe.isConsumed()).thenReturn(true);
            when(probe.getRemainingTokens()).thenReturn(10L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            var filter = new RateLimitFilter(proxyManager, () -> null, objectMapper, Set.of());
            filter.doFilterInternal(request, response, filterChain);

            verify(bucketBuilder).build(eq("rl:ip:192.168.1.100"), any(Supplier.class));
        }
    }

    @Nested
    @DisplayName("X-Forwarded-For trust")
    class XffTrust {

        @Test
        @DisplayName("ignores XFF when remote addr is not a trusted proxy")
        void ignoresXffFromUntrustedSource() throws Exception {
            request.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");

            var probe = mock(ConsumptionProbe.class);
            when(probe.isConsumed()).thenReturn(true);
            when(probe.getRemainingTokens()).thenReturn(10L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            var filter = new RateLimitFilter(proxyManager, () -> null, objectMapper, Set.of());
            filter.doFilterInternal(request, response, filterChain);

            // Should use remoteAddr (192.168.1.100), not XFF (10.0.0.1)
            verify(bucketBuilder).build(eq("rl:ip:192.168.1.100"), any(Supplier.class));
        }

        @Test
        @DisplayName("uses XFF when remote addr is a trusted proxy")
        void usesXffFromTrustedProxy() throws Exception {
            request.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");

            var probe = mock(ConsumptionProbe.class);
            when(probe.isConsumed()).thenReturn(true);
            when(probe.getRemainingTokens()).thenReturn(10L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            // 192.168.1.100 is the remoteAddr and is trusted
            var filter = new RateLimitFilter(proxyManager, () -> null, objectMapper,
                    Set.of("192.168.1.100"));
            filter.doFilterInternal(request, response, filterChain);

            // Should use first XFF entry (10.0.0.1)
            verify(bucketBuilder).build(eq("rl:ip:10.0.0.1"), any(Supplier.class));
        }
    }
}
