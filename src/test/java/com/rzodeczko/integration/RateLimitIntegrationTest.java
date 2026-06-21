package com.rzodeczko.integration;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.domain.model.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "gateway.rate-limit.enabled=true",
        "gateway.rate-limit.requests-per-second=1",
        "gateway.rate-limit.burst-capacity=5",
        "gateway.routes[0].prefix=/auth",
        "gateway.routes[0].target=http://auth:8084",
        "gateway.public-paths[0]=POST:/auth/login",
        "gateway.cors.allowed-origins[0]=http://localhost:3000",
        "gateway.forwarding.connect-timeout-ms=2000",
        "gateway.forwarding.read-timeout-ms=10000",
        "gateway.forwarding-max-body-size=10485760"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final int BURST_CAPACITY = 5;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ForwardingPort forwardingPort;

    @BeforeEach
    void setUp() {
        when(forwardingPort.forward(any(), any()))
                .thenReturn(new GatewayResponse(200,
                        Map.of("content-type", List.of("application/json")),
                        "{\"ok\":true}".getBytes()));
    }

    @Test
    @DisplayName("returns 429 after burst capacity is exceeded")
    void returns429AfterBurstExceeded() throws Exception {
        exhaustRateLimit("10.99.0.1");

        var result = loginFrom("10.99.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many requests"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isNotNull();
    }

    @Test
    @DisplayName("different IPs have independent rate limits")
    void differentIpsHaveIndependentLimits() throws Exception {
        exhaustRateLimit("10.99.1.1");

        loginFrom("10.99.1.2")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("response includes X-Rate-Limit-Remaining header")
    void includesRateLimitRemainingHeader() throws Exception {
        var result = loginFrom("10.99.2.1")
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Rate-Limit-Remaining")).isNotNull();
        int remaining = Integer.parseInt(result.getResponse().getHeader("X-Rate-Limit-Remaining"));
        System.out.println(remaining);
        assertThat(remaining).isLessThan(BURST_CAPACITY);
    }

    private ResultActions loginFrom(String ip) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content("{}")
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                }));
    }

    private void exhaustRateLimit(String ip) throws Exception {
        for (int i = 0; i < BURST_CAPACITY; i++) {
            loginFrom(ip).andExpect(status().isOk());
        }
    }
}
