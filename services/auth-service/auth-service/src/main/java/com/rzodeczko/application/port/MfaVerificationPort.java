package com.rzodeczko.application.port;

public interface MfaVerificationPort {
    boolean verify(String mfaSecret, int code);
}
