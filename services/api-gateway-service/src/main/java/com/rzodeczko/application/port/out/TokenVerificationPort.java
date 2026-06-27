package com.rzodeczko.application.port.out;

import com.rzodeczko.domain.model.TokenInfo;

// Output Port — weryfikacja tokenu JWT.
public interface TokenVerificationPort {
    TokenInfo verify(String token);
}
