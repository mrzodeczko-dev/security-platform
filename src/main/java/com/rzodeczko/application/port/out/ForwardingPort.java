package com.rzodeczko.application.port.out;

import com.rzodeczko.domain.model.GatewayRequest;
import com.rzodeczko.domain.model.GatewayResponse;

// Output Port — przekazywanie requestu do downstream serwisu.
public interface ForwardingPort {
    GatewayResponse forward(String targetBaseUrl, GatewayRequest request);
}
