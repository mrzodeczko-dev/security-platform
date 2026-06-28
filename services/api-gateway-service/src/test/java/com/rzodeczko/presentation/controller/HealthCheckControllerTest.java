package com.rzodeczko.presentation.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthCheckController")
class HealthCheckControllerTest {

    private final HealthCheckController controller = new HealthCheckController();

    @Test
    @DisplayName("healthCheck returns 200 with message")
    void healthCheckReturns200() {
        var response = controller.healthCheck();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("API GATEWAY SERVICE OK");
    }
}
