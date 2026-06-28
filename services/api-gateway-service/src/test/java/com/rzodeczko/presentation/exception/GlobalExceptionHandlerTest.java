package com.rzodeczko.presentation.exception;

import com.rzodeczko.domain.exception.DownstreamUnavailableException;
import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.exception.PayloadTooLargeException;
import com.rzodeczko.domain.exception.RouteNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler (api-gateway)")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleRouteNotFound → 404")
    void routeNotFound() {
        var ex = new RouteNotFoundException("/unknown/path");

        var response = handler.handleRouteNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", ex.getMessage());
    }

    @Test
    @DisplayName("handleInvalidToken → 401")
    void invalidToken() {
        var ex = new InvalidTokenException("expired");

        var response = handler.handleInvalidToken(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Unauthorized");
    }

    @Test
    @DisplayName("handlePayloadTooLarge → 413")
    void payloadTooLarge() {
        var ex = new PayloadTooLargeException(1048576);

        var response = handler.handlePayloadTooLarge(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("error", ex.getMessage());
    }

    @Test
    @DisplayName("handleCircuitBreakerOpen → 503")
    void circuitBreakerOpen() {
        var ex = mock(CallNotPermittedException.class);
        when(ex.getMessage()).thenReturn("CircuitBreaker is OPEN");

        var response = handler.handleCircuitBreakerOpen(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "Service temporarily unavailable");
    }

    @Test
    @DisplayName("handleDownstreamUnavailable → 502")
    void downstreamUnavailable() {
        var ex = new DownstreamUnavailableException("user-service", "connection refused");

        var response = handler.handleDownstreamUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsEntry("error", "Service temporarily unavailable");
    }

    @Test
    @DisplayName("handleNoResource → 404")
    void noResource() {
        var ex = mock(NoResourceFoundException.class);
        when(ex.getMessage()).thenReturn("No static resource missing.");

        var response = handler.handleNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("handleUnexpectedException → 500")
    void unexpected() {
        var ex = new RuntimeException("oops");

        var response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Gateway error");
    }
}
