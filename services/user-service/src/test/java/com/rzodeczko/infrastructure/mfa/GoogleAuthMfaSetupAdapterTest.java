package com.rzodeczko.infrastructure.mfa;

import com.rzodeczko.infrastructure.configuration.properties.MfaProperties;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthMfaSetupAdapterTest {

    @Mock
    private GoogleAuthenticator googleAuthenticator;

    private GoogleAuthMfaSetupAdapter adapter;

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final MfaProperties PROPERTIES = new MfaProperties("MyApp");

    @BeforeEach
    void setUp() {
        adapter = new GoogleAuthMfaSetupAdapter(googleAuthenticator, PROPERTIES);
    }

    private GoogleAuthenticatorKey buildRealKey() {
        return new GoogleAuthenticatorKey.Builder(SECRET)
                .setConfig(new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().build())
                .build();
    }

    @Test
    @DisplayName("should call googleAuthenticator.createCredentials()")
    void shouldCallCreateCredentials() {
        // given
        when(googleAuthenticator.createCredentials()).thenReturn(buildRealKey());

        // when
        adapter.generateCredentials("testuser");

        // then
        verify(googleAuthenticator).createCredentials();
    }

    @Test
    @DisplayName("result should contain the secret from the generated key")
    void resultShouldContainSecretFromKey() {
        // given
        when(googleAuthenticator.createCredentials()).thenReturn(buildRealKey());

        // when
        var result = adapter.generateCredentials("testuser");

        // then
        assertThat(result.secret()).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("result qrUrl should not be null and contain expected components")
    void resultQrUrlShouldNotBeNull() {
        // given
        when(googleAuthenticator.createCredentials()).thenReturn(buildRealKey());

        // when
        var result = adapter.generateCredentials("testuser");

        // then
        assertThat(result.qrUrl()).isNotNull();
        assertThat(result.qrUrl()).contains("testuser");
        assertThat(result.qrUrl()).contains("MyApp");
    }
}
