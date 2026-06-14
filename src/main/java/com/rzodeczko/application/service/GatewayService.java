package com.rzodeczko.application.service;

import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;

/** Input port — routes an incoming request with authenticated user context. */
public interface GatewayService {
    GatewayResponse handle(GatewayRequest request, String userId, String username, String userRole);
}
