package com.rzodeczko.infrastructure.mfa;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GoogleAuthMfaVerificationAdapter")
class GoogleAuthMfaVerificationAdapterTest {

    private final GoogleAuthenticator googleAuthenticator = mock(GoogleAuthenticator.class);
    private final GoogleAuthMfaVerificationAdapter adapter = new GoogleAuthMfaVerificationAdapter(googleAuthenticator);

    @Test
    @DisplayName("should return true when GoogleAuthenticator authorizes")
    void verifyValidCode() {
        when(googleAuthenticator.authorize("SECRET", 123456)).thenReturn(true);

        assertThat(adapter.verify("SECRET", 123456)).isTrue();
    }

    @Test
    @DisplayName("should return false when GoogleAuthenticator rejects")
    void verifyInvalidCode() {
        when(googleAuthenticator.authorize("SECRET", 999999)).thenReturn(false);

        assertThat(adapter.verify("SECRET", 999999)).isFalse();
    }
}
