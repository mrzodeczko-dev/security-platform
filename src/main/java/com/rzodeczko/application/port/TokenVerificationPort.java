package com.rzodeczko.application.port;


import com.rzodeczko.domain.model.TokenInfo;

/** Output port for JWT token verification. */
public interface TokenVerificationPort {
    TokenInfo verify(String token);
}
