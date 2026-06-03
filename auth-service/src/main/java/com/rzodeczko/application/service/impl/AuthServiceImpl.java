package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.command.LoginCommand;
import com.rzodeczko.application.command.RefreshTokenCommand;
import com.rzodeczko.application.command.VerifyMfaCommand;
import com.rzodeczko.application.dto.LoginResultDto;
import com.rzodeczko.application.dto.TokenPairDto;
import com.rzodeczko.application.port.MfaCachePort;
import com.rzodeczko.application.port.MfaVerificationPort;
import com.rzodeczko.application.port.TokenPort;
import com.rzodeczko.application.port.UserVerificationPort;
import com.rzodeczko.application.service.AuthService;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.exception.MfaAuthorizationFailedException;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.domain.model.TokenType;

public class AuthServiceImpl implements AuthService {

    private final UserVerificationPort userVerificationPort;
    private final TokenPort tokenPort;
    private final MfaCachePort mfaCachePort;
    private final MfaVerificationPort mfaVerificationPort;

    public AuthServiceImpl(
            UserVerificationPort userVerificationPort,
            TokenPort tokenPort,
            MfaCachePort mfaCachePort,
            MfaVerificationPort mfaVerificationPort) {
        this.userVerificationPort = userVerificationPort;
        this.tokenPort = tokenPort;
        this.mfaCachePort = mfaCachePort;
        this.mfaVerificationPort = mfaVerificationPort;
    }

    @Override
    public LoginResultDto login(LoginCommand command) {
        var credentials = userVerificationPort.verifyCredentials(
                command.username(),
                command.password()
        );

        if (credentials.mfaRequired()) {
            return LoginResultDto.mfaRequired(command.username());
        }

        var tokens = tokenPort.generate(
                credentials.userId(),
                credentials.username(),
                credentials.role()
        );

        return LoginResultDto.withTokens(tokens);
    }

    @Override
    public LoginResultDto verifyMfa(VerifyMfaCommand command) {
        MfaData mfaData = mfaCachePort.get(command.username())
                .orElseGet(() -> {
                    var data = userVerificationPort.getMfaData(command.username());
                    mfaCachePort.put(command.username(), data);
                    return data;
                });

        if (!mfaVerificationPort.verify(mfaData.mfaSecret(), command.code())) {
            throw new MfaAuthorizationFailedException();
        }

        var tokens = tokenPort.generate(
                mfaData.userId(),
                mfaData.username(),
                mfaData.role()
        );

        return LoginResultDto.withTokens(tokens);
    }

    @Override
    public TokenPairDto refresh(RefreshTokenCommand command) {
        var tokenInfo = tokenPort.parse(command.refreshToken());

        if (tokenInfo.type() != TokenType.REFRESH) {
            throw new InvalidTokenException(
                    "Expected refresh token, got: " + tokenInfo.type()
            );
        }

        return tokenPort.generate(
                tokenInfo.userId(),
                tokenInfo.username(),
                tokenInfo.role()
        );
    }
}
