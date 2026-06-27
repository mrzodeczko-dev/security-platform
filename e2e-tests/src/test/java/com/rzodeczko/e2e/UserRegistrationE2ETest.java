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
 * E2E tests covering the user registration and activation flow
 * through the API Gateway → User Service → MySQL + MailHog.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserRegistrationE2ETest extends AbstractE2ETest {

    private static final String USERNAME = "e2e_user_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String EMAIL = USERNAME + "@e2e-test.com";
    private static final String PASSWORD = "SecurePass123!";

    @BeforeAll
    static void cleanMailbox() {
        MailhogClient.deleteAll();
    }

    // -- Registration---------------------------------------------------------

    @Test
    @Order(1)
    void shouldRegisterNewUser() {
        api.register(USERNAME, EMAIL, PASSWORD)
                .then()
                .statusCode(201)
                .body("data", equalTo(USERNAME))
                .body("error", nullValue());
    }

    @Test
    @Order(2)
    void shouldRejectDuplicateRegistration() {
        api.register(USERNAME, EMAIL, PASSWORD)
                .then()
                .statusCode(anyOf(is(400), is(409)));
    }

    @Test
    @Order(3)
    void shouldRejectRegistrationWithInvalidEmail() {
        var client = new ApiClient();
        client.given()
                .body(Map.of(
                        "username", "bad_email_user",
                        "email", "not-an-email",
                        "password", PASSWORD,
                        "passwordConfirmation", PASSWORD
                ))
                .post("/users")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(4)
    void shouldRejectRegistrationWithShortPassword() {
        var client = new ApiClient();
        client.given()
                .body(Map.of(
                        "username", "short_pass_user",
                        "email", "short@e2e.com",
                        "password", "ab",
                        "passwordConfirmation", "ab"
                ))
                .post("/users")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(5)
    void shouldRejectRegistrationWithMismatchedPasswords() {
        var client = new ApiClient();
        client.given()
                .body(Map.of(
                        "username", "mismatch_user",
                        "email", "mismatch@e2e.com",
                        "password", PASSWORD,
                        "passwordConfirmation", "DifferentPass123!"
                ))
                .post("/users")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(6)
    void shouldRejectRegistrationWithMissingFields() {
        var client = new ApiClient();
        client.given()
                .body(Map.of("username", "incomplete_user"))
                .post("/users")
                .then()
                .statusCode(400);
    }

    // -- Activation-----------------------------------------------------------

    @Test
    @Order(10)
    void shouldReceiveActivationEmailAndActivate() {
        // Wait for the activation email to arrive in MailHog
        var code = await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> MailhogClient.getActivationCode(EMAIL),
                        java.util.Optional::isPresent);

        // Activate the user
        api.given()
                .body(Map.of("code", code.get()))
                .post("/users/activation")
                .then()
                .statusCode(200)
                .body("data", notNullValue());
    }

    @Test
    @Order(11)
    void shouldRejectActivationWithInvalidCode() {
        api.given()
                .body(Map.of("code", "000000"))
                .post("/users/activation")
                .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    // --- Login after activation ---

    @Test
    @Order(20)
    void shouldLoginAfterActivation() {
        api.login(USERNAME, PASSWORD)
                .then()
                .statusCode(201)
                .body("data.mfaRequired", is(false))
                .body("data.accessToken.accessToken", notNullValue());
    }

    @Test
    @Order(21)
    void shouldNotLoginBeforeActivation() {
        // Register a new user but don't activate
        var username = "unactivated_" + UUID.randomUUID().toString().substring(0, 8);
        var email = username + "@e2e-test.com";
        api.register(username, email, PASSWORD);

        var freshClient = new ApiClient();
        freshClient.login(username, PASSWORD)
                .then()
                .statusCode(anyOf(is(400), is(401), is(403)));
    }
}
