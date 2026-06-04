package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.command.LoginCommand;
import com.rzodeczko.application.command.RefreshTokenCommand;
import com.rzodeczko.application.command.VerifyMfaCommand;
import com.rzodeczko.application.dto.TokenPairDto;
import com.rzodeczko.application.port.MfaCachePort;
import com.rzodeczko.application.port.MfaVerificationPort;
import com.rzodeczko.application.port.TokenPort;
import com.rzodeczko.application.port.UserVerificationPort;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.exception.MfaAuthorizationFailedException;
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

    @InjectMocks
    private AuthServiceImpl authService;

    private UUID userId;
    private final TokenPairDto tokenPair = new TokenPairDto("access-token", "refresh-token");

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void login_withoutMfa_returnsTokenPair() {
        var credentials = new UserCredentials(userId, "john", "USER", false);
        given(userVerificationPort.verifyCredentials("john", "pass")).willReturn(credentials);
        given(tokenPort.generate(userId, "john", "USER")).willReturn(tokenPair);

        var result = authService.login(new LoginCommand("john", "pass"));

        assertThat(result.mfaRequired()).isFalse();
        assertThat(result.tokens()).isEqualTo(tokenPair);
        assertThat(result.usernameForMfa()).isNull();
    }

    @Test
    void login_withMfaRequired_returnsMfaFlag_andSkipsTokenGeneration() {
        var credentials = new UserCredentials(userId, "john", "USER", true);
        given(userVerificationPort.verifyCredentials("john", "pass")).willReturn(credentials);

        var result = authService.login(new LoginCommand("john", "pass"));

        assertThat(result.mfaRequired()).isTrue();
        assertThat(result.usernameForMfa()).isEqualTo("john");
        assertThat(result.tokens()).isNull();
        then(tokenPort).shouldHaveNoInteractions();
    }


    @Test
    void verifyMfa_cacheHit_verifiesAndReturnsTokens() {
        var mfaData = new MfaData(userId, "john", "USER", "SECRET");
        given(mfaCachePort.get("john")).willReturn(Optional.of(mfaData));
        given(mfaVerificationPort.verify("SECRET", 482910)).willReturn(true);
        given(tokenPort.generate(userId, "john", "USER")).willReturn(tokenPair);

        var result = authService.verifyMfa(new VerifyMfaCommand("john", 482910));

        assertThat(result.tokens()).isEqualTo(tokenPair);
        then(userVerificationPort).shouldHaveNoInteractions();
    }

    @Test
    void verifyMfa_cacheMiss_fetchesFromUserServiceAndCaches() {
        var mfaData = new MfaData(userId, "john", "USER", "SECRET");
        given(mfaCachePort.get("john")).willReturn(Optional.empty());
        given(userVerificationPort.getMfaData("john")).willReturn(mfaData);
        given(mfaVerificationPort.verify("SECRET", 482910)).willReturn(true);
        given(tokenPort.generate(userId, "john", "USER")).willReturn(tokenPair);

        authService.verifyMfa(new VerifyMfaCommand("john", 482910));

        then(mfaCachePort).should().put("john", mfaData);
    }

    @Test
    void verifyMfa_invalidCode_throwsMfaAuthorizationFailedException() {
        var mfaData = new MfaData(userId, "john", "USER", "SECRET");
        given(mfaCachePort.get("john")).willReturn(Optional.of(mfaData));
        given(mfaVerificationPort.verify("SECRET", 111111)).willReturn(false);

        assertThatThrownBy(() -> authService.verifyMfa(new VerifyMfaCommand("john", 111111)))
                .isInstanceOf(MfaAuthorizationFailedException.class);

        then(tokenPort).shouldHaveNoInteractions();
    }


    @Test
    void refresh_validRefreshToken_returnsNewTokenPair() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.REFRESH);
        given(tokenPort.parse("refresh-jwt")).willReturn(tokenInfo);
        given(tokenPort.generate(userId, "john", "USER")).willReturn(tokenPair);

        var result = authService.refresh(new RefreshTokenCommand("refresh-jwt"));

        assertThat(result).isEqualTo(tokenPair);
    }

    @Test
    void refresh_accessTokenPassedAsRefresh_throwsInvalidTokenException() {
        var tokenInfo = new TokenInfo(userId, "john", "USER", TokenType.ACCESS);
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
}
