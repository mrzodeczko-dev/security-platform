package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.command.LoginCommand;
import com.rzodeczko.application.command.LogoutCommand;
import com.rzodeczko.application.command.RefreshTokenCommand;
import com.rzodeczko.application.command.VerifyMfaCommand;
import com.rzodeczko.application.dto.LoginResultDto;
import com.rzodeczko.application.dto.TokenPairDto;
import com.rzodeczko.application.port.MfaCachePort;
import com.rzodeczko.application.port.MfaVerificationPort;
import com.rzodeczko.application.port.RefreshTokenPort;
import com.rzodeczko.application.port.TokenPort;
import com.rzodeczko.application.port.UserVerificationPort;
import com.rzodeczko.application.service.AuthService;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.exception.MfaAuthorizationFailedException;
import com.rzodeczko.domain.exception.MfaSessionNotFoundException;
import com.rzodeczko.domain.exception.RefreshTokenRevokedException;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.domain.model.TokenType;

import java.util.UUID;

public class AuthServiceImpl implements AuthService {

    private final UserVerificationPort userVerificationPort;
    private final TokenPort tokenPort;
    private final MfaCachePort mfaCachePort;
    private final MfaVerificationPort mfaVerificationPort;
    private final RefreshTokenPort refreshTokenPort;

    public AuthServiceImpl(
            UserVerificationPort userVerificationPort,
            TokenPort tokenPort,
            MfaCachePort mfaCachePort,
            MfaVerificationPort mfaVerificationPort,
            RefreshTokenPort refreshTokenPort) {
        this.userVerificationPort = userVerificationPort;
        this.tokenPort = tokenPort;
        this.mfaCachePort = mfaCachePort;
        this.mfaVerificationPort = mfaVerificationPort;
        this.refreshTokenPort = refreshTokenPort;
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

        var familyId = UUID.randomUUID().toString();
        var tokens = tokenPort.generate(
                credentials.userId(),
                credentials.username(),
                credentials.role(),
                familyId
        );

        storeRefreshToken(tokens.refreshToken(), credentials.userId(), familyId);

        return LoginResultDto.withTokens(tokens);
    }

    @Override
    public LoginResultDto verifyMfa(VerifyMfaCommand command) {
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

        mfaCachePort.deleteMfaSession(command.mfaId());

        var familyId = UUID.randomUUID().toString();
        var tokens = tokenPort.generate(
                mfaData.userId(),
                mfaData.username(),
                mfaData.role(),
                familyId
        );

        storeRefreshToken(tokens.refreshToken(), mfaData.userId(), familyId);

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

        // Token reuse detection: if jti not in Redis, the token was already rotated.
        // An attacker may have stolen and used it - revoke the entire family.
        if (!refreshTokenPort.exists(tokenInfo.jti())) {
            if (tokenInfo.familyId() != null) {
                refreshTokenPort.deleteAllByFamilyId(tokenInfo.familyId());
            }
            throw new RefreshTokenRevokedException();
        }

        // Rotation: revoke old, issue new with same familyId
        refreshTokenPort.delete(tokenInfo.jti());

        var newTokens = tokenPort.generate(
                tokenInfo.userId(),
                tokenInfo.username(),
                tokenInfo.role(),
                tokenInfo.familyId()
        );

        storeRefreshToken(newTokens.refreshToken(), tokenInfo.userId(), tokenInfo.familyId());

        return newTokens;
    }

    @Override
    public void logout(LogoutCommand command) {
        try {
            var tokenInfo = tokenPort.parse(command.refreshToken());

            if (command.revokeAll() && tokenInfo.familyId() != null) {
                // Explicit "logout from all devices" - revoke the entire token family
                refreshTokenPort.deleteAllByFamilyId(tokenInfo.familyId());
            } else if (tokenInfo.jti() != null) {
                // Default single-session logout - revoke only this refresh token
                refreshTokenPort.delete(tokenInfo.jti());
            }
        } catch (InvalidTokenException e) {
            // Token already expired or invalid - nothing to revoke
        }
    }

    private void storeRefreshToken(String rawToken, UUID userId, String familyId) {
        var info = tokenPort.parse(rawToken);
        if (info.jti() != null) {
            refreshTokenPort.save(info.jti(), userId, familyId);
        }
    }
}
