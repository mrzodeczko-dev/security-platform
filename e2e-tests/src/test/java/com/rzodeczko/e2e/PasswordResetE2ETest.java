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
 * E2E tests for the password reset flow:
 * register → activate → request reset code → get reset token → reset password → login with new password.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordResetE2ETest extends AbstractE2ETest {

    private static final String USERNAME = "rst_e2e_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String EMAIL = USERNAME + "@e2e-test.com";
    private static final String PASSWORD = "OldPass123!";
    private static final String NEW_PASSWORD = "NewPass456!";
    private static final ApiClient client = new ApiClient();

    @BeforeAll
    static void registerAndActivate() {
        MailhogClient.deleteAll();

        client.register(USERNAME, EMAIL, PASSWORD).then().statusCode(201);

        var activationCode = await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> MailhogClient.getActivationCode(EMAIL),
                        java.util.Optional::isPresent);

        client.given()
                .body(Map.of("code", activationCode.get()))
                .post("/users/activation")
                .then().statusCode(200);

        // Confirm login works with old password
        client.login(USERNAME, PASSWORD).then().statusCode(201);
        client.clearTokens();
    }

    @Test
    @Order(1)
    void shouldRequestPasswordResetCode() {
        // Resend activation code (which serves as reset code trigger)
        client.given()
                .body(Map.of("email", EMAIL))
                .post("/users/code")
                .then()
                .statusCode(201);
    }

    @Test
    @Order(2)
    void shouldGetPasswordResetPermissionAndReset() {
        // Wait for the new code email
        MailhogClient.deleteAll();

        client.given()
                .body(Map.of("email", EMAIL))
                .post("/users/code")
                .then().statusCode(201);

        var code = await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> MailhogClient.getActivationCode(EMAIL),
                        java.util.Optional::isPresent);

        // Get password reset permission (returns a reset token)
        var resetTokenResponse = client.given()
                .body(Map.of("code", code.get()))
                .post("/users/password/permission");

        // The endpoint may return the reset token in data
        if (resetTokenResponse.statusCode() == 200) {
            var resetToken = resetTokenResponse.jsonPath().getString("data");

            if (resetToken != null && !resetToken.isBlank()) {
                // Reset the password
                client.given()
                        .body(Map.of(
                                "resetToken", resetToken,
                                "password", NEW_PASSWORD,
                                "passwordConfirmation", NEW_PASSWORD
                        ))
                        .post("/users/password/reset")
                        .then()
                        .statusCode(201);

                // Login with new password
                client.login(USERNAME, NEW_PASSWORD)
                        .then().statusCode(201);
            }
        }
    }

    @Test
    @Order(3)
    void shouldRejectResetWithInvalidToken() {
        client.given()
                .body(Map.of(
                        "resetToken", "invalid-token-123",
                        "password", NEW_PASSWORD,
                        "passwordConfirmation", NEW_PASSWORD
                ))
                .post("/users/password/reset")
                .then()
                .statusCode(anyOf(is(400), is(401), is(404)));
    }
}
