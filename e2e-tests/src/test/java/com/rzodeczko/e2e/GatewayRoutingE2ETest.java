package com.rzodeczko.e2e;

import com.rzodeczko.e2e.support.AbstractE2ETest;
import com.rzodeczko.e2e.support.ApiClient;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/**
 * E2E tests verifying that the API Gateway correctly routes requests
 * to the appropriate downstream services and handles edge cases.
 */
class GatewayRoutingE2ETest extends AbstractE2ETest {

    @Test
    void shouldReturn404ForUnknownRoute() {
        var client = new ApiClient();
        client.given()
                .when()
                .get("/nonexistent/path")
                .then()
                .statusCode(anyOf(is(401), is(403), is(404)));
    }

    @Test
    void shouldRouteHealthCheckToGateway() {
        var client = new ApiClient();
        client.given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void shouldRejectRequestWithoutContentTypeOnPost() {
        var client = new ApiClient();
        // POST to a public endpoint without proper JSON content type
        client.given()
                .contentType("text/plain")
                .body("not json")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(anyOf(is(400), is(415)));
    }

    @Test
    void shouldHandleCorsPreflightForAllowedOrigin() {
        var client = new ApiClient();
        client.given()
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,Authorization")
                .when()
                .options("/auth/login")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:3000"))
                .header("Access-Control-Allow-Methods", containsString("POST"));
    }

    @Test
    void shouldRejectCorsFromDisallowedOrigin() {
        var client = new ApiClient();
        var response = client.given()
                .header("Origin", "http://evil-site.com")
                .header("Access-Control-Request-Method", "POST")
                .when()
                .options("/auth/login");

        // Spring Security either blocks (403) or doesn't include CORS headers
        var corsHeader = response.header("Access-Control-Allow-Origin");
        if (corsHeader != null) {
            org.assertj.core.api.Assertions.assertThat(corsHeader)
                    .isNotEqualTo("http://evil-site.com");
        }
    }
}
