package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.MfaVerificationPort;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.infrastructure.cache.RedisMfaCacheAdapter;
import com.rzodeczko.infrastructure.cache.RedisRefreshTokenAdapter;
import com.rzodeczko.infrastructure.token.JwtTokenAdapter;
import com.rzodeczko.support.AbstractWireMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT extends AbstractWireMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RedisMfaCacheAdapter mfaCacheAdapter;
    @Autowired
    private RedisRefreshTokenAdapter refreshTokenAdapter;
    @Autowired
    private JwtTokenAdapter jwtTokenAdapter;

    @MockitoBean
    private MfaVerificationPort mfaVerificationPort;

    static final UUID USER_ID = UUID.randomUUID();
    static final String INTERNAL_SECRET = "test-internal-secret";
    static final String MFA_SECRET = "JBSWY3DPEHPK3PXP";
    static final String FAMILY_ID = UUID.randomUUID().toString();


    @Test
    void login_noMfa_returns201WithAccessTokenAndRefreshCookie() throws Exception {
        stubCredentials(false);

        mockMvc.perform(post("/auth/login").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"Secret123!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh-token"))
                .andExpect(cookie().httpOnly("refresh-token", true));
    }

    @Test
    void login_mfaRequired_returns200WithMfaIdAndFlag() throws Exception {
        stubCredentials(true);

        mockMvc.perform(post("/auth/login").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"Secret123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.mfaId").isNotEmpty())
                .andExpect(jsonPath("$.data.usernameForMfa").value("john"));
    }

    @Test
    void login_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/auth/login").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"Secret123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/login").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void login_userServiceReturns401_propagates401() throws Exception {
        getWireMockServer().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/users/credentials"))
                .withHeader("X-Internal-Secret", equalTo(INTERNAL_SECRET))
                .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

        mockMvc.perform(post("/auth/login").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void mfa_validMfaIdAndCode_returns201WithAccessTokenAndCookie() throws Exception {
        var mfaId = "test-mfa-session-id";
        mfaCacheAdapter.storeMfaSession(mfaId, "john");
        mfaCacheAdapter.put("john", new MfaData(USER_ID, "john", "USER", MFA_SECRET));
        given(mfaVerificationPort.verify(MFA_SECRET, 482910)).willReturn(true);

        mockMvc.perform(post("/auth/mfa").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mfaId":"%s","code":482910}
                                """.formatted(mfaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh-token"))
                .andExpect(cookie().httpOnly("refresh-token", true));
    }

    @Test
    void mfa_validMfaId_invalidCode_returns401() throws Exception {
        var mfaId = "test-mfa-session-id-2";
        mfaCacheAdapter.storeMfaSession(mfaId, "john");
        mfaCacheAdapter.put("john", new MfaData(USER_ID, "john", "USER", MFA_SECRET));
        given(mfaVerificationPort.verify(MFA_SECRET, 111111)).willReturn(false);

        mockMvc.perform(post("/auth/mfa").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mfaId":"%s","code":111111}
                                """.formatted(mfaId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void mfa_invalidMfaId_returns401() throws Exception {
        mockMvc.perform(post("/auth/mfa").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mfaId":"nonexistent-mfa-id","code":123456}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mfa_codeOutOfRange_returns400() throws Exception {
        mockMvc.perform(post("/auth/mfa").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mfaId":"some-id","code":99}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mfa_validMfaId_cacheMiss_fetchesFromUserService() throws Exception {
        var mfaId = "test-mfa-session-id-3";
        mfaCacheAdapter.storeMfaSession(mfaId, "john");
        // MFA data NOT in cache — will fall back to user-service
        getWireMockServer().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/users/mfa"))
                .withHeader("X-Internal-Secret", equalTo(INTERNAL_SECRET))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"userId":"%s","username":"john","role":"USER","mfaSecret":"%s"}
                                """.formatted(USER_ID, MFA_SECRET))));
        given(mfaVerificationPort.verify(MFA_SECRET, 482910)).willReturn(true);

        mockMvc.perform(post("/auth/mfa").header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mfaId":"%s","code":482910}
                                """.formatted(mfaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }


    @Test
    void refresh_validCookie_returns201WithNewToken() throws Exception {
        var tokens = jwtTokenAdapter.generate(USER_ID, "john", "USER", FAMILY_ID);
        var tokenInfo = jwtTokenAdapter.parse(tokens.refreshToken());
        refreshTokenAdapter.save(tokenInfo.jti(), USER_ID, FAMILY_ID);

        var cookie = new MockCookie("refresh-token", tokens.refreshToken());
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/refresh").header("X-Internal-Secret", INTERNAL_SECRET).cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh-token"));

        // Old JTI should be revoked after rotation
        assertThat(refreshTokenAdapter.exists(tokenInfo.jti())).isFalse();
    }

    @Test
    void refresh_revokedToken_returns401AndRevokesFamily() throws Exception {
        var tokens = jwtTokenAdapter.generate(USER_ID, "john", "USER", FAMILY_ID);
        // Do NOT store JTI — simulates revoked token (token reuse scenario)
        var cookie = new MockCookie("refresh-token", tokens.refreshToken());
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/refresh").header("X-Internal-Secret", INTERNAL_SECRET).cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_noCookie_returns400() throws Exception {
        mockMvc.perform(post("/auth/refresh").header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        var cookie = new MockCookie("refresh-token", "not-a-jwt");
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/refresh").header("X-Internal-Secret", INTERNAL_SECRET).cookie(cookie))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void logout_default_revokesSingleTokenOnly() throws Exception {
        var familyId = UUID.randomUUID().toString();
        var tokens = jwtTokenAdapter.generate(USER_ID, "john", "USER", familyId);
        var tokenInfo = jwtTokenAdapter.parse(tokens.refreshToken());
        refreshTokenAdapter.save(tokenInfo.jti(), USER_ID, familyId);

        // Store a second token in the same family to verify it survives default logout
        var secondJti = "second-jti-" + UUID.randomUUID();
        refreshTokenAdapter.save(secondJti, USER_ID, familyId);

        var cookie = new MockCookie("refresh-token", tokens.refreshToken());
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/logout").header("X-Internal-Secret", INTERNAL_SECRET).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Logged out successfully"))
                .andExpect(cookie().maxAge("refresh-token", 0));

        // Only the submitted token should be revoked; sibling token in the same family survives
        assertThat(refreshTokenAdapter.exists(tokenInfo.jti())).isFalse();
        assertThat(refreshTokenAdapter.exists(secondJti)).isTrue();
    }

    @Test
    void logout_revokeAll_revokesEntireFamily() throws Exception {
        var familyId = UUID.randomUUID().toString();
        var tokens = jwtTokenAdapter.generate(USER_ID, "john", "USER", familyId);
        var tokenInfo = jwtTokenAdapter.parse(tokens.refreshToken());
        refreshTokenAdapter.save(tokenInfo.jti(), USER_ID, familyId);

        var secondJti = "second-jti-" + UUID.randomUUID();
        refreshTokenAdapter.save(secondJti, USER_ID, familyId);

        var cookie = new MockCookie("refresh-token", tokens.refreshToken());
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/logout?revokeAll=true").header("X-Internal-Secret", INTERNAL_SECRET).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Logged out successfully"))
                .andExpect(cookie().maxAge("refresh-token", 0));

        // Both tokens in the family should be revoked
        assertThat(refreshTokenAdapter.exists(tokenInfo.jti())).isFalse();
        assertThat(refreshTokenAdapter.exists(secondJti)).isFalse();
    }

    @Test
    void logout_withoutCookie_returns200() throws Exception {
        mockMvc.perform(post("/auth/logout").header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Logged out successfully"))
                .andExpect(cookie().maxAge("refresh-token", 0));
    }


    private void stubCredentials(boolean mfaRequired) {
        getWireMockServer().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/users/credentials"))
                .withHeader("X-Internal-Secret", equalTo(INTERNAL_SECRET))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"userId":"%s","username":"john","role":"USER","mfaRequired":%b}
                                """.formatted(USER_ID, mfaRequired))));
    }
}
