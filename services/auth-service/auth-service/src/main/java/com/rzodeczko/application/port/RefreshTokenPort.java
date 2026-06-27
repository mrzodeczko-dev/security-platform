package com.rzodeczko.application.port;

import java.util.UUID;

public interface RefreshTokenPort {

    void save(String jti, UUID userId);

    boolean exists(String jti);

    void delete(String jti);
}
