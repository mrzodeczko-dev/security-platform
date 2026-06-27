package com.rzodeczko.e2e;

import com.rzodeczko.e2e.support.AbstractE2ETest;
import com.rzodeczko.e2e.support.ApiClient;
import com.rzodeczko.e2e.support.MailhogClient;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests for the authentication lifecycle:
 * login → token refresh → logout → verify tokens are invalidated.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationFlowE2ETest extends AbstractE2ETest {

    private static final String USERNAME = "auth_e2e_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String EMAIL = USERNAME + "@e2e-test.com";
    private static final String PASSWORD = "AuthPass123!";
    private static final ApiClient client = new ApiClient();

    @BeforeAll
    static void registerAndActivateUser() {
        MailhogClient.deleteAll();

        // Register
        client.register(USERNAME, EMAIL, PASSWORD)
                .then().statusCode(201);

        // Wait for activation code and activate
        var code = await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> MailhogClient.getActivationCode(EMAIL),
                        java.util.Optional::isPresent);

        client.given()
                .body(Map.of("code", code.get()))
                .post("/users/activation")
                .then().statusCode(200);
    }

    // ── Login ──────────────────────────────────────────────

    @Test
    @Order(1)
    void shouldLoginWithValidCredentials() {
        client.login(USERNAME, PASSWORD)
                .then()
                .statusCode(201)
                .body("data.mfaRequired", is(false))
                .body("data.accessToken.accessToken", notNullValue());

        assertThat(client.getAccessToken()).isNotBlank();
    }

    @Test
    @Order(2)
    void shouldRejectLoginWithWrongPassword() {
        var freshClient = new ApiClient();
        freshClient.login(USERNAME, "WrongPassword123!")
                .then()
                .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    @Order(3)
    void shouldRejectLoginWithNonExistentUser() {
        var freshClient = new ApiClient();
        freshClient.login("nonexistent_user_xyz", PASSWORD)
                .then()
                .statusCode(anyOf(is(400), is(401), is(404)));
    }

    // ── Token Refresh ──────────────────────────────────────

    @Test
    @Order(10)
    void shouldRefreshAccessToken() {
        // First login to get tokens
        client.login(USERNAME, PASSWORD)
                .then().statusCode(201);
        var originalToken = client.getAccessToken();

        // Refresh
        var response = client.refresh();
        response.then()
                .statusCode(201)
                .body("data.accessToken", notNullValue());

        assertThat(client.getAccessToken()).isNotBlank();
        // New token should be different
        assertThat(client.getAccessToken()).isNotEqualTo(originalToken);
    }

    @Test
    @Order(11)
    void shouldRejectRefreshWithoutCookie() {
        var freshClient = new ApiClient();
        freshClient.given()
                .post("/auth/refresh")
                .then()
                .statusCode(anyOf(is(400), is(401)));
    }

    // ── Logout ─────────────────────────────────────────────

    @Test
    @Order(20)
    void shouldLogoutSuccessfully() {
        client.login(USERNAME, PASSWORD).then().statusCode(201);

        client.logout()
                .then()
                .statusCode(200)
                .body("data", containsString("Logged out"));
    }

    @Test
    @Order(21)
    void shouldRejectRefreshAfterLogout() {
        // Login, then logout
        client.login(USERNAME, PASSWORD).then().statusCode(201);
        client.logout().then().statusCode(200);

        // Refresh should fail — token was invalidated
        client.refresh()
                .then()
                .statusCode(anyOf(is(400), is(401)));
    }

    // ── Validation ─────────────────────────────────────────

    @Test
    @Order(30)
    void shouldRejectLoginWithEmptyBody() {
        var freshClient = new ApiClient();
        freshClient.given()
                .body("{}")
                .post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(31)
    void shouldRejectLoginWithBlankUsername() {
        var freshClient = new ApiClient();
        freshClient.given()
                .body(Map.of("username", "", "password", PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(400);
    }
}
