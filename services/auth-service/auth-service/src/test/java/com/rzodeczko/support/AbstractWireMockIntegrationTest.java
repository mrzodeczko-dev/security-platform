package com.rzodeczko.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public abstract class AbstractWireMockIntegrationTest extends AbstractRedisIntegrationTest {
    private static final WireMockServer WIRE_MOCK = new WireMockServer(
            wireMockConfig().dynamicPort().http2PlainDisabled(true)
    );

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("user-service.url", () -> "http://localhost:" + WIRE_MOCK.port());
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    protected WireMockServer getWireMockServer() {
        return WIRE_MOCK;
    }
}
