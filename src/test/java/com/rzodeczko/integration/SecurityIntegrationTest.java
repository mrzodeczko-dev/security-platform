package com.rzodeczko.integration;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.domain.model.GatewayResponse;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ForwardingPort forwardingPort;

    @BeforeEach
    void setUp() {
        when(forwardingPort.forward(any(), any()))
                .thenReturn(new GatewayResponse(200,
                        Map.of("content-type", List.of("application/json")),
                        "{\"ok\":true}".getBytes()));
    }

    private String buildJwt(String role) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "testuser")
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(SECRET_KEY)
                .compact();
    }

    @Nested
    @DisplayName("Public paths")
    class PublicPaths {

        @Test
        @DisplayName("public endpoint accessible without token")
        void publicEndpointWithoutToken() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"a@b.com\",\"password\":\"pass\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("health check accessible without token")
        void healthCheckWithoutToken() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Protected paths without token")
    class ProtectedWithoutToken {

        @Test
        @DisplayName("admin endpoint returns 401 without token")
        void adminEndpointWithoutToken() throws Exception {
            mockMvc.perform(put("/users/123/role")
                            .contentType("application/json")
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("user endpoint returns 401 without token")
        void userEndpointWithoutToken() throws Exception {
            mockMvc.perform(post("/users/123/mfa")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("unclassified path returns 401 without token")
        void anyOtherPathWithoutToken() throws Exception {
            mockMvc.perform(get("/some/resource"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Role-based access control")
    class RoleBasedAccess {

        @Test
        @DisplayName("ROLE_ADMIN can access admin endpoints")
        void adminCanAccessAdminEndpoint() throws Exception {
            mockMvc.perform(put("/users/123/role")
                            .header("Authorization", "Bearer " + buildJwt("ROLE_ADMIN"))
                            .contentType("application/json")
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ROLE_USER cannot access admin endpoints")
        void userCannotAccessAdminEndpoint() throws Exception {
            mockMvc.perform(put("/users/123/role")
                            .header("Authorization", "Bearer " + buildJwt("ROLE_USER"))
                            .contentType("application/json")
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("ROLE_USER can access user endpoints")
        void userCanAccessUserEndpoint() throws Exception {
            mockMvc.perform(post("/users/123/mfa")
                            .header("Authorization", "Bearer " + buildJwt("ROLE_USER"))
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ROLE_ADMIN can access user endpoints")
        void adminCanAccessUserEndpoint() throws Exception {
            mockMvc.perform(post("/users/123/mfa")
                            .header("Authorization", "Bearer " + buildJwt("ROLE_ADMIN"))
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Invalid tokens")
    class InvalidTokens {

        @Test
        @DisplayName("expired token returns 401")
        void expiredTokenReturns401() throws Exception {
            var expiredToken = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("type", "access")
                    .claim("role", "ROLE_USER")
                    .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                    .signWith(SECRET_KEY)
                    .compact();

            mockMvc.perform(get("/users/123/mfa")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("malformed token returns 401")
        void malformedTokenReturns401() throws Exception {
            mockMvc.perform(get("/users/123/mfa")
                            .header("Authorization", "Bearer invalid.token.here"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("wrong signature returns 401")
        void wrongSignatureReturns401() throws Exception {
            byte[] otherKeyBytes = new byte[64];
            java.util.Arrays.fill(otherKeyBytes, (byte) 'X');
            var otherKey = new SecretKeySpec(otherKeyBytes, "HmacSHA512");

            var token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("type", "access")
                    .claim("role", "ROLE_USER")
                    .signWith(otherKey)
                    .compact();

            mockMvc.perform(get("/users/123/mfa")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }
}
