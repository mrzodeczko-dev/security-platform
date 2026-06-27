package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.domain.exception.RouteNotFoundException;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import com.rzodeczko.domain.model.RoutingTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayServiceImplTest {

    @Mock
    private ForwardingPort forwardingPort;

    private GatewayServiceImpl gatewayService;

    private static final RoutingTable ROUTING_TABLE = new RoutingTable(List.of(
            new RoutingTable.Route("/auth", "http://auth:8084"),
            new RoutingTable.Route("/users", "http://users:8083")
    ));

    @BeforeEach
    void setUp() {
        gatewayService = new GatewayServiceImpl(forwardingPort, ROUTING_TABLE);
    }

    private GatewayResponse stubDownstreamResponse() {
        return new GatewayResponse(200, Map.of("content-type", List.of("application/json")), "{}".getBytes());
    }

    @Nested
    @DisplayName("Routing")
    class Routing {

        @Test
        @DisplayName("forwards to correct target based on path prefix")
        void forwardsToCorrectTarget() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", Map.of(), new byte[0], null),
                    null, null, null);

            verify(forwardingPort).forward(eq("http://auth:8084"), any());
        }

        @Test
        @DisplayName("throws RouteNotFoundException for unknown path")
        void throwsForUnknownRoute() {
            assertThatThrownBy(() -> gatewayService.handle(
                    new GatewayRequest("/orders/1", "GET", Map.of(), new byte[0], null),
                    null, null, null))
                    .isInstanceOf(RouteNotFoundException.class)
                    .hasMessageContaining("/orders/1");
        }
    }

    @Nested
    @DisplayName("Request header filtering")
    class RequestHeaderFiltering {

        @Test
        @DisplayName("forwards only safe headers (content-type, accept, cache-control)")
        void forwardsOnlySafeHeaders() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            var headers = Map.of(
                    "content-type", List.of("application/json"),
                    "accept", List.of("*/*"),
                    "cache-control", List.of("no-cache"),
                    "authorization", List.of("Bearer secret"),
                    "host", List.of("evil.com")
            );
            gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", headers, new byte[0], null),
                    null, null, null);

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());
            var forwarded = captor.getValue().headers();

            assertThat(forwarded).containsKeys("content-type", "accept", "cache-control");
            assertThat(forwarded).doesNotContainKeys("authorization", "host");
        }

        @Test
        @DisplayName("does not forward cookie header")
        void doesNotForwardCookie() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            var headers = Map.of(
                    "cookie", List.of("session=abc123; tracking=xyz"),
                    "content-type", List.of("application/json")
            );
            gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", headers, new byte[0], null),
                    null, null, null);

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());

            assertThat(captor.getValue().headers()).doesNotContainKey("cookie");
        }
    }

    @Nested
    @DisplayName("X-Request-Id")
    class XRequestId {

        @Test
        @DisplayName("reuses client-provided X-Request-Id")
        void reusesClientRequestId() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            var headers = Map.of("X-Request-Id", List.of("client-id-123"));
            gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", headers, new byte[0], null),
                    null, null, null);

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());

            assertThat(captor.getValue().headers().get("X-Request-Id"))
                    .containsExactly("client-id-123");
        }

        @Test
        @DisplayName("generates X-Request-Id when not provided")
        void generatesRequestId() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", Map.of(), new byte[0], null),
                    null, null, null);

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());

            assertThat(captor.getValue().headers().get("X-Request-Id"))
                    .hasSize(1)
                    .first().asString().isNotBlank();
        }

        @Test
        @DisplayName("propagates X-Request-Id to response")
        void propagatesRequestIdToResponse() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            var headers = Map.of("X-Request-Id", List.of("trace-42"));
            var response = gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", headers, new byte[0], null),
                    null, null, null);

            assertThat(response.headers().get("X-Request-Id"))
                    .containsExactly("trace-42");
        }
    }

    @Nested
    @DisplayName("User headers enrichment")
    class UserHeaders {

        @Test
        @DisplayName("adds X-User-* headers for authenticated user")
        void addsUserHeaders() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            gatewayService.handle(
                    new GatewayRequest("/users/me", "GET", Map.of(), new byte[0], null),
                    "user-42", "john", "ROLE_USER");

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());
            var forwarded = captor.getValue().headers();

            assertThat(forwarded.get("X-User-Id")).containsExactly("user-42");
            assertThat(forwarded.get("X-User-Name")).containsExactly("john");
            assertThat(forwarded.get("X-User-Role")).containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("omits X-User-* headers for anonymous request")
        void omitsUserHeadersForAnonymous() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", Map.of(), new byte[0], null),
                    null, null, null);

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());
            var forwarded = captor.getValue().headers();

            assertThat(forwarded).doesNotContainKeys("X-User-Id", "X-User-Name", "X-User-Role");
        }
    }

    @Nested
    @DisplayName("Response header stripping")
    class ResponseHeaderStripping {

        @Test
        @DisplayName("strips hop-by-hop and server-revealing headers from response")
        void stripsUnsafeResponseHeaders() {
            var downstreamHeaders = Map.of(
                    "content-type", List.of("application/json"),
                    "server", List.of("nginx/1.25"),
                    "x-powered-by", List.of("Express"),
                    "transfer-encoding", List.of("chunked"),
                    "connection", List.of("keep-alive"),
                    "x-custom", List.of("allowed")
            );
            when(forwardingPort.forward(any(), any()))
                    .thenReturn(new GatewayResponse(200, downstreamHeaders, "{}".getBytes()));

            var response = gatewayService.handle(
                    new GatewayRequest("/auth/login", "POST", Map.of(), new byte[0], null),
                    null, null, null);

            assertThat(response.headers())
                    .containsKeys("content-type", "x-custom", "X-Request-Id")
                    .doesNotContainKeys("server", "x-powered-by", "transfer-encoding", "connection");
        }
    }

    @Nested
    @DisplayName("Query string propagation")
    class QueryStringPropagation {

        @Test
        @DisplayName("forwards query string unchanged")
        void forwardsQueryString() {
            when(forwardingPort.forward(any(), any())).thenReturn(stubDownstreamResponse());

            gatewayService.handle(
                    new GatewayRequest("/users", "GET", Map.of(), new byte[0], "page=2&size=10"),
                    null, null, null);

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(forwardingPort).forward(any(), captor.capture());

            assertThat(captor.getValue().queryString()).isEqualTo("page=2&size=10");
        }
    }
}
