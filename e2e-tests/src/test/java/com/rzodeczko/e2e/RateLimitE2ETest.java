package com.rzodeczko.e2e;

import com.rzodeczko.e2e.support.AbstractE2ETest;
import com.rzodeczko.e2e.support.ApiClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test verifying that the API Gateway enforces rate limiting.
 * The e2e docker-compose sets RPS=50, burst=100.
 * Sending a burst well above the limit should trigger 429 responses.
 */
class RateLimitE2ETest extends AbstractE2ETest {

    @Test
    void shouldEnforceRateLimitingOnBurst() {
        var client = new ApiClient();
        int totalRequests = 200;
        int tooManyRequests = 0;

        for (int i = 0; i < totalRequests; i++) {
            var statusCode = client.given()
                    .get("/actuator/health")
                    .statusCode();

            if (statusCode == 429) {
                tooManyRequests++;
            }
        }

        // With burst=100 and 200 rapid requests, some should be rate-limited
        assertThat(tooManyRequests)
                .as("Expected some requests to be rate-limited (HTTP 429)")
                .isGreaterThan(0);
    }
}
