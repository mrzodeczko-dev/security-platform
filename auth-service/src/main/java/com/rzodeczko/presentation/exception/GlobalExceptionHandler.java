package com.rzodeczko.presentation.exception;

import com.rzodeczko.domain.exception.InvalidTokenException;
import com.rzodeczko.domain.exception.MfaAuthorizationFailedException;
import com.rzodeczko.domain.exception.UserServiceUnavailableException;
import com.rzodeczko.presentation.dto.response.ApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleValidation(MethodArgumentNotValidException e) {
        var message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiResponseDto.error(message));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidToken(InvalidTokenException e) {
        log.warn("Invalid token: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(MfaAuthorizationFailedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMfaFailed(MfaAuthorizationFailedException e) {
        log.warn("MFA authorization failed: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUserServiceError(RestClientResponseException e) {
        log.warn("User service returned: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
        return ResponseEntity
                .status(e.getStatusCode())
                .body(ApiResponseDto.error(e.getResponseBodyAsString()));
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnavailable(UserServiceUnavailableException e) {
        log.error("User service unavailable: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error in auth-service", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error("Unexpected error occurred"));
    }
}
