package com.rzodeczko.e2e;

import com.rzodeczko.e2e.support.AbstractE2ETest;
import com.rzodeczko.e2e.support.ApiClient;
import com.rzodeczko.e2e.support.MailhogClient;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests covering security enforcement:
 * - Unauthenticated access to protected endpoints returns 401
 * - Non-admin users cannot access admin endpoints (403)
 * - JWT tampering is rejected
 * - Internal endpoints are not reachable via gateway
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityE2ETest extends AbstractE2ETest {

    private static final String USERNAME = "sec_e2e_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String EMAIL = USERNAME + "@e2e-test.com";
    private static final String PASSWORD = "SecPass123!";
    private static final ApiClient authenticatedClient = new ApiClient();

    @BeforeAll
    static void setupUser() {
        MailhogClient.deleteAll();

        authenticatedClient.register(USERNAME, EMAIL, PASSWORD).then().statusCode(201);

        var code = await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> MailhogClient.getActivationCode(EMAIL),
                        java.util.Optional::isPresent);

        authenticatedClient.given()
                .body(Map.of("code", code.get()))
                .post("/users/activation")
                .then().statusCode(200);

        authenticatedClient.login(USERNAME, PASSWORD).then().statusCode(201);
    }

    // --- Unauthenticated access ---

    @Test
    @Order(1)
    void shouldRejectUnauthenticatedMfaSetup() {
        var unauthClient = new ApiClient();
        unauthClient.given()
                .when()
                .post("/users/" + UUID.randomUUID() + "/mfa")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(2)
    void shouldRejectUnauthenticatedRoleChange() {
        var unauthClient = new ApiClient();
        unauthClient.given()
                .body(Map.of("newRole", "ADMIN"))
                .when()
                .put("/users/" + UUID.randomUUID() + "/role")
                .then()
                .statusCode(401);
    }

    // --- Authorization (role-based) ---

    @Test
    @Order(10)
    void shouldRejectRoleChangeByNonAdmin() {
        // The authenticated user is ROLE_USER, not ADMIN
        authenticatedClient.given()
                .body(Map.of("newRole", "ADMIN"))
                .when()
                .put("/users/" + UUID.randomUUID() + "/role")
                .then()
                .statusCode(403);
    }

    // --- JWT tampering ---

    @Test
    @Order(20)
    void shouldRejectTamperedJwt() {
        var tamperedClient = new ApiClient();
        tamperedClient.setAccessToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJoYWNrZXIifQ.tampered_signature");

        tamperedClient.given()
                .when()
                .post("/users/" + UUID.randomUUID() + "/mfa")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(21)
    void shouldRejectMalformedAuthorizationHeader() {
        var client = new ApiClient();
        client.given()
                .header("Authorization", "NotBearer some-random-string")
                .when()
                .post("/users/" + UUID.randomUUID() + "/mfa")
                .then()
                .statusCode(401);
    }

    // --- Public endpoints remain accessible ---

    @Test
    @Order(30)
    void shouldAllowPublicHealthCheck() {
        var client = new ApiClient();
        client.given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(31)
    void shouldAllowPublicRegistration() {
        var publicUsername = "pub_" + UUID.randomUUID().toString().substring(0, 8);
        var client = new ApiClient();
        client.register(publicUsername, publicUsername + "@e2e.com", PASSWORD)
                .then()
                .statusCode(201);
    }

    // --- Internal endpoints should NOT be accessible via gateway ---

    @Test
    @Order(40)
    void shouldNotExposeInternalCredentialsEndpoint() {
        // The gateway routes /users/** to user-service, but /internal/**
        // should not be routed — the gateway only maps /auth/** and /users/**
        var client = new ApiClient();
        client.given()
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when()
                .post("/internal/users/credentials")
                .then()
                .statusCode(anyOf(is(401), is(403), is(404)));
    }
}
