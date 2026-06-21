package com.rzodeczko.application.service;

import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;

/**
 * Application input port for the gateway use case.
 *
 * <p>Accepts a domain request and trusted user data, returns the downstream response.
 */
public interface GatewayService {
    GatewayResponse handle(GatewayRequest request, String userId, String username, String userRole);
}
