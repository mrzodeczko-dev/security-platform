package com.rzodeczko.infrastructure.mfa;


import com.rzodeczko.application.port.MfaVerificationPort;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleAuthMfaVerificationAdapter implements MfaVerificationPort {

    private final GoogleAuthenticator googleAuthenticator;

    @Override
    public boolean verify(String mfaSecret, int code) {
        return googleAuthenticator.authorize(mfaSecret, code);
    }
}
