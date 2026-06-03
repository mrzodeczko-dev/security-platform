package com.rzodeczko.application.port;

import com.rzodeczko.domain.model.MfaData;

import java.util.Optional;

public interface MfaCachePort {
    Optional<MfaData> get(String username);
    void put(String username, MfaData mfaData);
    void invalidate(String username);
}
