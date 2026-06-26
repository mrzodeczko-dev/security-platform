package com.rzodeczko.presentation.exception;

import com.rzodeczko.domain.exception.*;
import com.rzodeczko.presentation.dto.response.ApiResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // --- handleValidation ---

    @Test
    @DisplayName("handleValidation - returns 400 with field error message")
    void handleValidation_returnsBadRequestWithFieldErrorMessage() {
        var ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = mock(BindingResult.class);
        var fieldError = new FieldError("obj", "email", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("email: must not be blank");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("handleValidation - multiple field errors are joined with comma")
    void handleValidation_multipleFieldErrors_joinedWithComma() {
        var ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = mock(BindingResult.class);
        var fieldError1 = new FieldError("obj", "email", "must not be blank");
        var fieldError2 = new FieldError("obj", "username", "size must be between 3 and 50");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error())
                .isEqualTo("email: must not be blank, username: size must be between 3 and 50");
    }

    // --- handleNotFound ---

    @Test
    @DisplayName("handleNotFound - UserNotFoundException returns 404 with message")
    void handleNotFound_userNotFoundException_returns404() {
        var ex = new UserNotFoundException("john");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("User not found: john");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("handleNotFound - VerificationCodeNotFoundException returns 404")
    void handleNotFound_verificationCodeNotFoundException_returns404() {
        var ex = new VerificationCodeNotFoundException();

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Verification code not found");
    }

    // --- handleConflict ---

    @Test
    @DisplayName("handleConflict - UsernameAlreadyExistsException returns 409")
    void handleConflict_usernameAlreadyExists_returns409() {
        var ex = new UsernameAlreadyExistsException("john");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Username already exists: john");
    }

    @Test
    @DisplayName("handleConflict - EmailAlreadyExistsException returns 409")
    void handleConflict_emailAlreadyExists_returns409() {
        var ex = new EmailAlreadyExistsException("john@example.com");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Email already exists: john@example.com");
    }

    @Test
    @DisplayName("handleConflict - UserAlreadyActivatedException returns 409")
    void handleConflict_userAlreadyActivated_returns409() {
        var ex = new UserAlreadyActivatedException("john");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("User already activated: john");
    }

    @Test
    @DisplayName("handleConflict - MfaAlreadyActivatedException returns 409")
    void handleConflict_mfaAlreadyActivated_returns409() {
        var ex = new MfaAlreadyActivatedException();

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("MFA already activated");
    }

    // --- handleBadRequest ---

    @Test
    @DisplayName("handleBadRequest - PasswordMismatchException returns 400")
    void handleBadRequest_passwordMismatch_returns400() {
        var ex = new PasswordMismatchException();

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Passwords do not match");
    }

    @Test
    @DisplayName("handleBadRequest - VerificationCodeExpiredException returns 400")
    void handleBadRequest_verificationCodeExpired_returns400() {
        var ex = new VerificationCodeExpiredException();

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Verification code has expired");
    }

    // --- handleUnauthorized ---

    @Test
    @DisplayName("handleUnauthorized - InvalidCredentialsException returns 401")
    void handleUnauthorized_invalidCredentials_returns401() {
        var ex = new InvalidCredentialsException();

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Invalid username or password");
    }

    @Test
    @DisplayName("handleUnauthorized - UserNotActivatedException returns 401")
    void handleUnauthorized_userNotActivated_returns401() {
        var ex = new UserNotActivatedException("john");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("User is not activated: john");
    }

    // --- handleForbidden ---

    @Test
    @DisplayName("handleForbidden - InsufficientRoleException returns 403 with required role")
    void handleForbidden_insufficientRole_returns403() {
        var ex = new InsufficientRoleException("ROLE_ADMIN");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Insufficient role. Required: ROLE_ADMIN");
    }

    // --- handleRoleMismatch ---

    @Test
    @DisplayName("handleRoleMismatch - RoleMismatchException returns 403 with mismatch details")
    void handleRoleMismatch_returns403() {
        var ex = new RoleMismatchException("ROLE_USER", "ROLE_ADMIN");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleRoleMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("ROLE_USER").contains("ROLE_ADMIN");
    }

    // --- handleNoResource ---

    @Test
    @DisplayName("handleNoResource - NoResourceFoundException returns 404")
    void handleNoResource_returns404() throws NoResourceFoundException {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/nonexistent", "/nonexistent");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isNotBlank();
    }

    // --- handleUnexpected ---

    @Test
    @DisplayName("handleUnexpected - generic Exception returns 500 with fixed message")
    void handleUnexpected_returns500WithGenericMessage() {
        var ex = new Exception("something broke");

        ResponseEntity<ApiResponseDto<Void>> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Unexpected error occurred");
        assertThat(response.getBody().data()).isNull();
    }
}
