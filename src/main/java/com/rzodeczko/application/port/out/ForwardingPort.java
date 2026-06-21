package com.rzodeczko.application.port.out;


import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;

/** Output port for forwarding requests to downstream services. */
public interface ForwardingPort {
    GatewayResponse forward(String targetBaseUrl, GatewayRequest request);
}
