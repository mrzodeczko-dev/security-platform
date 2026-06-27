package com.rzodeczko.application.port;

import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.domain.model.UserCredentials;

public interface UserVerificationPort {
    UserCredentials verifyCredentials(String username, String password);
    MfaData getMfaData(String username);
}
