package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.MfaVerificationPort;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.infrastructure.cache.RedisMfaCacheAdapter;
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
    private JwtTokenAdapter jwtTokenAdapter;

    @MockitoBean
    private MfaVerificationPort mfaVerificationPort;

    static final UUID USER_ID = UUID.randomUUID();
    static final String INTERNAL_SECRET = "test-internal-secret";
    static final String MFA_SECRET = "JBSWY3DPEHPK3PXP";


    @Test
    void login_noMfa_returns201WithAccessTokenAndRefreshCookie() throws Exception {
        stubCredentials(false);

        mockMvc.perform(post("/auth/login")
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
    void login_mfaRequired_returns200WithMfaFlag() throws Exception {
        stubCredentials(true);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"Secret123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.usernameForMfa").value("john"));
    }

    @Test
    void login_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"Secret123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
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

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void mfa_validCode_returns201WithAccessTokenAndCookie() throws Exception {
        mfaCacheAdapter.put("john", new MfaData(USER_ID, "john", "USER", MFA_SECRET));
        given(mfaVerificationPort.verify(MFA_SECRET, 482910)).willReturn(true);

        mockMvc.perform(post("/auth/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","code":482910}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh-token"))
                .andExpect(cookie().httpOnly("refresh-token", true));
    }

    @Test
    void mfa_invalidCode_returns401() throws Exception {
        mfaCacheAdapter.put("john", new MfaData(USER_ID, "john", "USER", MFA_SECRET));
        given(mfaVerificationPort.verify(MFA_SECRET, 111111)).willReturn(false);

        mockMvc.perform(post("/auth/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","code":111111}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void mfa_codeOutOfRange_returns400() throws Exception {
        mockMvc.perform(post("/auth/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","code":99}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mfa_userNotInCacheAndUserServiceReturns404_propagates404() throws Exception {
        // Cache is empty — service falls back to /internal/users/mfa
        getWireMockServer().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/users/mfa"))
                .withHeader("X-Internal-Secret", equalTo(INTERNAL_SECRET))
                .willReturn(aResponse().withStatus(404).withBody("Not found")));

        mockMvc.perform(post("/auth/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nonexistent","code":123456}
                                """))
                .andExpect(status().isNotFound());
    }


    @Test
    void refresh_validCookie_returns201WithNewToken() throws Exception {
        var tokens = jwtTokenAdapter.generate(USER_ID, "john", "USER");
        var cookie = new MockCookie("refresh-token", tokens.refreshToken());
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/refresh").cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh-token"));
    }

    @Test
    void refresh_noCookie_returns400() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        var cookie = new MockCookie("refresh-token", "not-a-jwt");
        cookie.setPath("/auth/refresh");

        mockMvc.perform(post("/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void logout_returns200AndExpiresRefreshCookie() throws Exception {
        mockMvc.perform(post("/auth/logout"))
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
