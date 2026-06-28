package com.rzodeczko.infrastructure.adapter;

import com.rzodeczko.domain.exception.UserServiceUnavailableException;
import com.rzodeczko.infrastructure.configuration.properties.UserServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("UserServiceAdapter")
class UserServiceAdapterTest {

    private static final String BASE_URL = "http://localhost:9999";
    private static final String INTERNAL_SECRET = "test-secret";

    private MockRestServiceServer server;
    private UserServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new UserServiceProperties(BASE_URL, 500L, 1000L, INTERNAL_SECRET);
        adapter = new UserServiceAdapter(builder, properties);
    }

    @Test
    @DisplayName("verifyCredentials — success")
    void verifyCredentialsSuccess() {
        var userId = UUID.randomUUID();
        var json = """
                {"userId":"%s","username":"john","role":"ROLE_USER","mfaRequired":false}
                """.formatted(userId);

        server.expect(requestTo(BASE_URL + "/internal/users/credentials"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Secret", INTERNAL_SECRET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var result = adapter.verifyCredentials("john", "pass123");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("john");
        assertThat(result.role()).isEqualTo("ROLE_USER");
        assertThat(result.mfaRequired()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("verifyCredentials — mfaRequired=true")
    void verifyCredentialsMfaRequired() {
        var userId = UUID.randomUUID();
        var json = """
                {"userId":"%s","username":"john","role":"ROLE_USER","mfaRequired":true}
                """.formatted(userId);

        server.expect(requestTo(BASE_URL + "/internal/users/credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var result = adapter.verifyCredentials("john", "pass123");

        assertThat(result.mfaRequired()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("verifyCredentials — user-service returns 401 → RestClientResponseException propagated")
    void verifyCredentials401() {
        server.expect(requestTo(BASE_URL + "/internal/users/credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest().body("Invalid credentials"));

        assertThatThrownBy(() -> adapter.verifyCredentials("john", "wrong"))
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }

    @Test
    @DisplayName("getMfaData — success")
    void getMfaDataSuccess() {
        var userId = UUID.randomUUID();
        var json = """
                {"userId":"%s","username":"john","role":"ROLE_USER","mfaSecret":"JBSWY3DPEHPK3PXP"}
                """.formatted(userId);

        server.expect(requestTo(BASE_URL + "/internal/users/mfa"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Secret", INTERNAL_SECRET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var result = adapter.getMfaData("john");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("john");
        assertThat(result.mfaSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        server.verify();
    }

    @Test
    @DisplayName("getMfaData — user-service returns 404 → RestClientResponseException propagated")
    void getMfaData404() {
        server.expect(requestTo(BASE_URL + "/internal/users/mfa"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound().body("User not found"));

        assertThatThrownBy(() -> adapter.getMfaData("unknown"))
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }
}
