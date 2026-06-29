package com.rzodeczko.application.port;

import com.rzodeczko.application.dto.TokenPairDto;
import com.rzodeczko.domain.model.TokenInfo;

import java.util.UUID;

public interface TokenPort {
    TokenPairDto generate(UUID userId, String username, String role, String familyId);
    TokenInfo parse(String token);
}
