package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.infrastructure.configuration.properties.GatewayProperties;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BeanConfiguration")
class BeanConfigurationTest {

    private final BeanConfiguration config = new BeanConfiguration();

    @Test
    @DisplayName("secretKey — valid 64-byte key returns HmacSHA512 SecretKey")
    void secretKeyValid() {
        byte[] keyBytes = new byte[64];
        java.util.Arrays.fill(keyBytes, (byte) 'A');
        var props = new JwtProperties(Base64.getEncoder().encodeToString(keyBytes));

        var key = config.secretKey(props);

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA512");
        assertThat(key.getEncoded()).hasSize(64);
    }

    @Test
    @DisplayName("secretKey — key shorter than 64 bytes throws IllegalStateException")
    void secretKeyTooShort() {
        byte[] shortKey = new byte[32];
        var props = new JwtProperties(Base64.getEncoder().encodeToString(shortKey));

        assertThatThrownBy(() -> config.secretKey(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 bytes");
    }

    @Test
    @DisplayName("objectMapper — returns non-null ObjectMapper")
    void objectMapper() {
        assertThat(config.objectMapper()).isNotNull();
    }

    @Test
    @DisplayName("routingTable — maps GatewayProperties routes to RoutingTable")
    void routingTable() {
        var routes = List.of(
                new GatewayProperties.RouteDefinition("/auth", "http://auth:8084"),
                new GatewayProperties.RouteDefinition("/users", "http://users:8083")
        );
        var props = new GatewayProperties(routes, null, null, null, null, null, 0, null);

        var table = config.routingTable(props);

        assertThat(table.routes()).hasSize(2);
        assertThat(table.resolveTarget("/auth/login")).contains("http://auth:8084");
        assertThat(table.resolveTarget("/users/me")).contains("http://users:8083");
    }
}
