package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.command.LoginCommand;
import com.rzodeczko.application.command.LogoutCommand;
import com.rzodeczko.application.command.RefreshTokenCommand;
import com.rzodeczko.application.command.VerifyMfaCommand;
import com.rzodeczko.application.dto.TokenPairDto;
import com.rzodeczko.application.port.MfaCachePort;
import com.rzodeczko.application.port.MfaVerificationPort;
import com.rzodeczko.application.port.RefreshTokenPort;
import com.rzodeczko.application.port.TokenPort;
import com.rzodeczko.application.port.UserVerificationPort;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.exception.MfaAuthorizationFailedException;
import com.rzodeczko.domain.exception.MfaSessionNotFoundException;
import com.rzodeczko.domain.exception.RefreshTokenRevokedException;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.domain.model.TokenInfo;
import com.rzodeczko.domain.model.TokenType;
import com.rzodeczko.domain.model.UserCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserVerificationPort userVerificationPort;
    @Mock
    private TokenPort tokenPort;
    @Mock
    private MfaCachePort mfaCachePort;
    @Mock
    private MfaVerificationPort mfaVerificationPort;
    @Mock
    private RefreshTokenPort refreshTokenPort;

    @InjectMocks
    private AuthServiceImpl authService;

    private UUID userId;
    private static final String JTI = "test-jti-uuid";
    private static final String FAMILY_ID = "test-family-id";
    private final TokenPairDto tokenPair = new TokenPairDto("access-token", "refresh-token");

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // ==========================================
    // login
    // ==========================================

    @Test
    void login_withoutMfa_returnsTokenPairAndStoresRefreshTokenWithFamilyId() {
        var credentials = new UserCredentials(userId, "john", "USER", false);
        given(userVerificationPort.verifyCredentials("john", "pass")).willReturn(credentials);
        given(tokenPort.generate(eq(userId), eq("john"), eq("USER"), any(String.class)))
                .willReturn(tokenPair);
        given(tokenPort.parse("refresh-token")).willReturn(
                new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, FAMILY_ID));

        var result = authService.login(new LoginCommand("john", "pass"));

        assertThat(result.mfaRequired()).isFalse();
        assertThat(result.tokens()).isEqualTo(tokenPair);
        then(refreshTokenPort).should().save(eq(JTI), eq(userId), any(String.class));
    }

    @Test
    void login_withMfaRequired_returnsMfaIdAndFlag_andSkipsTokenGeneration() {
        var credentials = new UserCredentials(userId, "john", "USER", true);
        given(userVerificationPort.verifyCredentials("john", "pass")).willReturn(credentials);

        var result = authService.login(new LoginCommand("john", "pass"));

        assertThat(result.mfaRequired()).isTrue();
        assertThat(result.mfaId()).isNotBlank();
        assertThat(result.usernameForMfa()).isEqualTo("john");
        assertThat(result.tokens()).isNull();
        then(tokenPort).shouldHaveNoInteractions();
        then(refreshTokenPort).shouldHaveNoInteractions();
        then(mfaCachePort).should().storeMfaSession(result.mfaId(), "john");
    }

    // ==========================================
    // verifyMfa
    // ==========================================

    @Test
    void verifyMfa_validMfaId_cacheHit_verifiesAndStoresRefreshToken() {
        var mfaId = "test-mfa-id";
        var mfaData = new MfaData(userId, "john", "USER", "SECRET");
        given(mfaCachePort.getMfaSession(mfaId)).willReturn(Optional.of("john"));
        given(mfaCachePort.get("john")).willReturn(Optional.of(mfaData));
        given(mfaVerificationPort.verify("SECRET", 482910)).willReturn(true);
        given(tokenPort.generate(eq(userId), eq("john"), eq("USER"), any(String.class)))
                .willReturn(tokenPair);
        given(tokenPort.parse("refresh-token")).willReturn(
                new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, FAMILY_ID));

        var result = authService.verifyMfa(new VerifyMfaCommand(mfaId, 482910));

        assertThat(result.tokens()).isEqualTo(tokenPair);
        then(mfaCachePort).should().deleteMfaSession(mfaId);
        then(refreshTokenPort).should().save(eq(JTI), eq(userId), any(String.class));
    }

    @Test
    void verifyMfa_validMfaId_cacheMiss_fetchesFromUserServiceAndCaches() {
        var mfaId = "test-mfa-id";
        var mfaData = new MfaData(userId, "john", "USER", "SECRET");
        given(mfaCachePort.getMfaSession(mfaId)).willReturn(Optional.of("john"));
        given(mfaCachePort.get("john")).willReturn(Optional.empty());
        given(userVerificationPort.getMfaData("john")).willReturn(mfaData);
        given(mfaVerificationPort.verify("SECRET", 482910)).willReturn(true);
        given(tokenPort.generate(eq(userId), eq("john"), eq("USER"), any(String.class)))
                .willReturn(tokenPair);
        given(tokenPort.parse("refresh-token")).willReturn(
                new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, FAMILY_ID));

        authService.verifyMfa(new VerifyMfaCommand(mfaId, 482910));

        then(mfaCachePort).should().put("john", mfaData);
        then(mfaCachePort).should().deleteMfaSession(mfaId);
        then(refreshTokenPort).should().save(eq(JTI), eq(userId), any(String.class));
    }

    @Test
    void verifyMfa_invalidMfaId_throwsMfaSessionNotFoundException() {
        given(mfaCachePort.getMfaSession("invalid-id")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyMfa(new VerifyMfaCommand("invalid-id", 482910)))
                .isInstanceOf(MfaSessionNotFoundException.class);

        then(tokenPort).shouldHaveNoInteractions();
        then(mfaVerificationPort).shouldHaveNoInteractions();
    }

    @Test
    void verifyMfa_validMfaId_invalidCode_throwsMfaAuthorizationFailedException() {
        var mfaId = "test-mfa-id";
        var mfaData = new MfaData(userId, "john", "USER", "SECRET");
        given(mfaCachePort.getMfaSession(mfaId)).willReturn(Optional.of("john"));
        given(mfaCachePort.get("john")).willReturn(Optional.of(mfaData));
        given(mfaVerificationPort.verify("SECRET", 111111)).willReturn(false);

        assertThatThrownBy(() -> authService.verifyMfa(new VerifyMfaCommand(mfaId, 111111)))
                .isInstanceOf(MfaAuthorizationFailedException.class);

        then(tokenPort).shouldHaveNoInteractions();
    }

    // ==========================================
    // refresh
    // ==========================================

    @Test
    void refresh_validRefreshToken_rotatesAndReturnsNewTokenPairWithSameFamilyId() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, FAMILY_ID);
        var newTokenPair = new TokenPairDto("new-access", "new-refresh");
        var newJti = "new-jti";
        given(tokenPort.parse("refresh-jwt")).willReturn(tokenInfo);
        given(refreshTokenPort.exists(JTI)).willReturn(true);
        given(tokenPort.generate(userId, "john", "USER", FAMILY_ID)).willReturn(newTokenPair);
        given(tokenPort.parse("new-refresh")).willReturn(
                new TokenInfo(userId, "john", "USER", TokenType.REFRESH, newJti, FAMILY_ID));

        var result = authService.refresh(new RefreshTokenCommand("refresh-jwt"));

        assertThat(result).isEqualTo(newTokenPair);
        then(refreshTokenPort).should().delete(JTI);
        then(refreshTokenPort).should().save(newJti, userId, FAMILY_ID);
    }

    @Test
    void refresh_revokedToken_deletesEntireFamilyAndThrows() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, FAMILY_ID);
        given(tokenPort.parse("revoked-jwt")).willReturn(tokenInfo);
        given(refreshTokenPort.exists(JTI)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenCommand("revoked-jwt")))
                .isInstanceOf(RefreshTokenRevokedException.class);

        then(refreshTokenPort).should().deleteAllByFamilyId(FAMILY_ID);
    }

    @Test
    void refresh_revokedTokenWithoutFamilyId_throwsWithoutFamilyCleanup() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, null);
        given(tokenPort.parse("old-jwt")).willReturn(tokenInfo);
        given(refreshTokenPort.exists(JTI)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenCommand("old-jwt")))
                .isInstanceOf(RefreshTokenRevokedException.class);

        then(refreshTokenPort).should().exists(JTI);
        then(refreshTokenPort).shouldHaveNoMoreInteractions();
    }

    @Test
    void refresh_accessTokenPassedAsRefresh_throwsInvalidTokenException() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.ACCESS, null, null);
        given(tokenPort.parse("access-jwt")).willReturn(tokenInfo);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenCommand("access-jwt")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Expected refresh token");
    }

    @Test
    void refresh_malformedToken_throwsInvalidTokenException() {
        given(tokenPort.parse("garbage")).willThrow(new InvalidTokenException("bad signature"));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenCommand("garbage")))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ==========================================
    // logout
    // ==========================================

    @Test
    void logout_validTokenWithFamilyId_deletesEntireFamily() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, FAMILY_ID);
        given(tokenPort.parse("refresh-jwt")).willReturn(tokenInfo);

        authService.logout(new LogoutCommand("refresh-jwt"));

        then(refreshTokenPort).should().deleteAllByFamilyId(FAMILY_ID);
    }

    @Test
    void logout_validTokenWithoutFamilyId_deletesSingleToken() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.REFRESH, JTI, null);
        given(tokenPort.parse("refresh-jwt")).willReturn(tokenInfo);

        authService.logout(new LogoutCommand("refresh-jwt"));

        then(refreshTokenPort).should().delete(JTI);
    }

    @Test
    void logout_expiredOrInvalidToken_doesNotThrow() {
        given(tokenPort.parse("expired-jwt")).willThrow(new InvalidTokenException("expired"));

        authService.logout(new LogoutCommand("expired-jwt"));

        then(refreshTokenPort).shouldHaveNoInteractions();
    }
}
