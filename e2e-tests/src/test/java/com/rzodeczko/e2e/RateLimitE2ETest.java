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
 * The e2e docker-compose sets RPS=10, burst=10.
 * We first drain the bucket with a warm-up, then send a burst
 * that must exceed capacity and trigger 429 responses.
 */
class RateLimitE2ETest extends AbstractE2ETest {

    @Test
    void shouldEnforceRateLimitingOnBurst() throws Exception {
        var baseUrl = E2ETestEnvironment.gatewayBaseUrl();

        // Warm-up: drain the token bucket
        for (int i = 0; i < 15; i++) {
            RestAssured.given().baseUri(baseUrl).get("/actuator/health");
        }

        // Brief pause to let in-flight requests settle
        Thread.sleep(200);

        // Burst: send 50 requests concurrently — bucket can hold at most ~10
        int burstSize = 50;
        var statusCodes = new CopyOnWriteArrayList<Integer>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < burstSize; i++) {
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
        Thread.sleep(2000);
    }
}
