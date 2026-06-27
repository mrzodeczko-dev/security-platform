package com.rzodeczko.e2e;

import com.rzodeczko.e2e.support.AbstractE2ETest;
import com.rzodeczko.e2e.support.E2ETestEnvironment;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test verifying that the API Gateway enforces rate limiting.
 * The e2e docker-compose sets RPS=100, burst=100.
 * Sending 300 concurrent requests should exceed the bucket capacity
 * and trigger 429 responses for the overflow.
 */
class RateLimitE2ETest extends AbstractE2ETest {

    @Test
    void shouldEnforceRateLimitingOnBurst() throws Exception {
        int totalRequests = 300;
        var statusCodes = new CopyOnWriteArrayList<Integer>();
        var baseUrl = E2ETestEnvironment.gatewayBaseUrl();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < totalRequests; i++) {
                futures.add(executor.submit(() -> {
                    var code = RestAssured.given()
                            .baseUri(baseUrl)
                            .when()
                            .get("/actuator/health")
                            .statusCode();
                    statusCodes.add(code);
                }));
            }
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        }

        long tooManyRequests = statusCodes.stream().filter(s -> s == 429).count();
        assertThat(tooManyRequests)
                .as("Expected some requests to be rate-limited (HTTP 429)")
                .isGreaterThan(0);

        // Let the bucket refill before the next test class runs
        Thread.sleep(1500);
    }
}
