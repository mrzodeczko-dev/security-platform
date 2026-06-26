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
import com.rzodeczko.domain.exception.MfaSessionNotFoundException;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.domain.model.TokenType;

import java.util.UUID;

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
            var mfaId = UUID.randomUUID().toString();
            mfaCachePort.storeMfaSession(mfaId, command.username());
            return LoginResultDto.mfaRequired(mfaId, command.username());
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
        // Verify mfaId is bound to a valid login session
        var username = mfaCachePort.getMfaSession(command.mfaId())
                .orElseThrow(MfaSessionNotFoundException::new);

        MfaData mfaData = mfaCachePort.get(username)
                .orElseGet(() -> {
                    var data = userVerificationPort.getMfaData(username);
                    mfaCachePort.put(username, data);
                    return data;
                });

        if (!mfaVerificationPort.verify(mfaData.mfaSecret(), command.code())) {
            throw new MfaAuthorizationFailedException();
        }

        // One-time use: delete MFA session after successful verification
        mfaCachePort.deleteMfaSession(command.mfaId());

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
