package com.rzodeczko.presentation.exception;

import com.rzodeczko.domain.exception.*;
import com.rzodeczko.presentation.dto.response.ApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler({UserNotFoundException.class, VerificationCodeNotFoundException.class})
    public ResponseEntity<ApiResponseDto<Void>> handleNotFound(RuntimeException e) {
        log.warn("Not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler({
            UsernameAlreadyExistsException.class,
            EmailAlreadyExistsException.class,
            UserAlreadyActivatedException.class,
            MfaAlreadyActivatedException.class
    })
    public ResponseEntity<ApiResponseDto<Void>> handleConflict(RuntimeException e) {
        log.warn("Conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler({PasswordMismatchException.class, VerificationCodeExpiredException.class})
    public ResponseEntity<ApiResponseDto<Void>> handleBadRequest(RuntimeException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler({InvalidCredentialsException.class, UserNotActivatedException.class})
    public ResponseEntity<ApiResponseDto<Void>> handleUnauthorized(RuntimeException e) {
        log.warn("Unauthorized: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InsufficientRoleException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleForbidden(InsufficientRoleException e) {
        log.warn("Forbidden: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(RoleMismatchException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleRoleMismatch(RoleMismatchException e) {
        log.warn("Security: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error in user-service", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error("Unexpected error occurred"));
    }
}
