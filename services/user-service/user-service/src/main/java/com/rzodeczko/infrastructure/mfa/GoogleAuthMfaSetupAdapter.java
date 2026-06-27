package com.rzodeczko.infrastructure.mfa;

import com.rzodeczko.application.dto.MfaSetupResultDto;
import com.rzodeczko.application.port.MfaSetupPort;
import com.rzodeczko.infrastructure.configuration.properties.MfaProperties;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthMfaSetupAdapter implements MfaSetupPort {

    private final GoogleAuthenticator googleAuthenticator;
    private final MfaProperties properties;

    @Override
    public MfaSetupResultDto generateCredentials(String username) {
        log.debug("Generating MFA credentials for username={}", username);

        var key = googleAuthenticator.createCredentials();
        var secret = key.getKey();
        var qrUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(
                properties.issuer(), username, key
        );
        return new MfaSetupResultDto(secret, qrUrl);
    }
}
