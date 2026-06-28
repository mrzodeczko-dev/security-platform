package com.rzodeczko.presentation.exception;

import com.rzodeczko.domain.exception.*;
import com.rzodeczko.presentation.dto.response.ApiResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler (auth-service)")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleUnsupportedMediaType → 415")
    void unsupportedMediaType() {
        var ex = new HttpMediaTypeNotSupportedException("text/plain");

        var response = handler.handleUnsupportedMediaType(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("text/plain");
    }

    @Test
    @DisplayName("handleMessageNotReadable → 400")
    void messageNotReadable() {
        var ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("bad body");

        var response = handler.handleMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Malformed request body");
    }

    @Test
    @DisplayName("handleValidation → 400 with field error details")
    void validation() {
        var ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("login", "username", "must not be blank"),
                new FieldError("login", "password", "must not be blank")
        ));

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error())
                .contains("username: must not be blank")
                .contains("password: must not be blank");
    }

    @Test
    @DisplayName("handleMissingCookie → 400")
    void missingCookie() {
        var param = mock(org.springframework.core.MethodParameter.class);
        when(param.getNestedParameterType()).thenReturn((Class) String.class);
        var ex = new MissingRequestCookieException("refreshToken", param);

        var response = handler.handleMissingCookie(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("handleInvalidToken → 401")
    void invalidToken() {
        var ex = new InvalidTokenException("expired");

        var response = handler.handleInvalidToken(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("expired");
    }

    @Test
    @DisplayName("handleMfaFailed → 401")
    void mfaFailed() {
        var ex = new MfaAuthorizationFailedException();

        var response = handler.handleMfaFailed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("handleMfaSessionNotFound → 401")
    void mfaSessionNotFound() {
        var ex = new MfaSessionNotFoundException();

        var response = handler.handleMfaSessionNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("handleRefreshTokenRevoked → 401")
    void refreshTokenRevoked() {
        var ex = new RefreshTokenRevokedException();

        var response = handler.handleRefreshTokenRevoked(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("handleUserServiceError → upstream status propagated")
    void userServiceError() {
        var ex = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found",
                org.springframework.http.HttpHeaders.EMPTY,
                "user not found".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        var response = handler.handleUserServiceError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("user not found");
    }

    @Test
    @DisplayName("handleUnavailable → 502")
    void unavailable() {
        var ex = new UserServiceUnavailableException("connection refused");

        var response = handler.handleUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("handleNoResource → 404")
    void noResource() {
        var ex = mock(NoResourceFoundException.class);
        when(ex.getMessage()).thenReturn("No static resource unknown.");

        var response = handler.handleNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("handleUnexpected → 500")
    void unexpected() {
        var ex = new RuntimeException("oops");

        var response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Unexpected error occurred");
    }
}
