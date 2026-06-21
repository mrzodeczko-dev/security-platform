package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GatewayPort;
import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;
import com.rzodeczko.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GatewayControllerTest {

    private static final long MAX_BODY_SIZE = 10_485_760L;

    @Mock
    private GatewayPort gatewayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new GatewayController(gatewayService, MAX_BODY_SIZE);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("Request forwarding")
    class RequestForwarding {

        @Test
        @DisplayName("GET request returns downstream response")
        void getRequestReturnsDownstreamResponse() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenReturn(new GatewayResponse(200,
                            Map.of("content-type", List.of("application/json")),
                            "{\"status\":\"ok\"}".getBytes()));

            mockMvc.perform(get("/auth/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"status\":\"ok\"}"));
        }

        @Test
        @DisplayName("POST request forwards body")
        void postRequestForwardsBody() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenReturn(new GatewayResponse(201,
                            Map.of("content-type", List.of("application/json")),
                            "{}".getBytes()));

            mockMvc.perform(post("/users")
                            .contentType("application/json")
                            .content("{\"email\":\"test@test.com\"}"))
                    .andExpect(status().isCreated());

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(gatewayService).handle(captor.capture(), isNull(), isNull(), isNull());

            assertThat(new String(captor.getValue().body())).isEqualTo("{\"email\":\"test@test.com\"}");
        }

        @Test
        @DisplayName("GET request sends empty body")
        void getRequestSendsEmptyBody() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenReturn(new GatewayResponse(200, Map.of(), new byte[0]));

            mockMvc.perform(get("/auth/login"))
                    .andExpect(status().isOk());

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(gatewayService).handle(captor.capture(), isNull(), isNull(), isNull());

            assertThat(captor.getValue().body()).isEmpty();
        }

        @Test
        @DisplayName("propagates downstream response headers")
        void propagatesResponseHeaders() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenReturn(new GatewayResponse(200,
                            Map.of("X-Custom-Header", List.of("custom-value")),
                            new byte[0]));

            mockMvc.perform(get("/auth/login"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Custom-Header", "custom-value"));
        }

        @Test
        @DisplayName("forwards query string")
        void forwardsQueryString() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenReturn(new GatewayResponse(200, Map.of(), new byte[0]));

            mockMvc.perform(get("/users?page=2&size=10"))
                    .andExpect(status().isOk());

            var captor = ArgumentCaptor.forClass(GatewayRequest.class);
            verify(gatewayService).handle(captor.capture(), isNull(), isNull(), isNull());

            assertThat(captor.getValue().queryString()).isEqualTo("page=2&size=10");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("returns 404 for RouteNotFoundException")
        void returns404ForRouteNotFound() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenThrow(new com.rzodeczko.domain.exception.RouteNotFoundException("/unknown"));

            mockMvc.perform(get("/unknown"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("returns 502 for DownstreamUnavailableException")
        void returns502ForDownstreamUnavailable() throws Exception {
            when(gatewayService.handle(any(), isNull(), isNull(), isNull()))
                    .thenThrow(new com.rzodeczko.domain.exception.DownstreamUnavailableException("auth-service", "Connection refused"));

            mockMvc.perform(get("/auth/login"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("Service temporarily unavailable"));
        }
    }
}
