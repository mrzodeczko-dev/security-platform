package com.rzodeczko.application.port.out;


import com.rzodeczko.domain.model.TokenInfo;

/** Output port for JWT token verification. */
public interface TokenVerificationPort {
    TokenInfo verify(String token);
}
