package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.command.ChangeUserRoleCommand;
import com.rzodeczko.application.command.RegisterUserCommand;
import com.rzodeczko.application.command.ResetPasswordCommand;
import com.rzodeczko.application.service.UserService;
import com.rzodeczko.presentation.dto.request.*;
import com.rzodeczko.presentation.dto.response.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {
    private final UserService userService;

    public UserController(@Qualifier("transactionalUserService") UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<String>> register(
            @Valid @RequestBody RegisterUserRequestDto req
    ) {
        var username = userService.register(new RegisterUserCommand(
                req.username(),
                req.email(),
                req.password(),
                req.passwordConfirmation()
        ));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.data(username));
    }

    @PostMapping("/activation")
    public ResponseEntity<ApiResponseDto<String>> activate(
            @Valid @RequestBody ActivateUserRequestDto req
    ) {
        return ResponseEntity
                .ok(ApiResponseDto.data(userService.activate(req.code())));
    }

    @PostMapping("/code")
    public ResponseEntity<ApiResponseDto<String>> resendActivationCode(
            @Valid @RequestBody ResendActivationCodeRequestDto req
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.data(userService.resendActivationCode(req.email())));
    }

    @PostMapping("/password/permission")
    public ResponseEntity<ApiResponseDto<String>> getPasswordResetPermission(
            @Valid @RequestBody PasswordResetPermissionRequestDto req
    ) {
        return ResponseEntity
                .ok(ApiResponseDto.data(userService.getPasswordResetPermission(req.code())));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponseDto<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto req
    ) {
        var username = userService.resetPassword(
                new ResetPasswordCommand(req.resetToken(), req.password(), req.passwordConfirmation()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.data(username));
    }

    @PostMapping("{userId}/mfa")
    public ResponseEntity<ApiResponseDto<String>> setupMfa(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponseDto.data(userService.setupMfa(userId)));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<ApiResponseDto<String>> changeUserRole(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeUserRoleRequestDto req,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String requestingUserRole
    ) {
        UUID requestingUserId = null;
        if (requestingUserIdStr != null) {
            try {
                requestingUserId = UUID.fromString(requestingUserIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-User-Id header value: {}", requestingUserIdStr);
            }
        }

        var username = userService.changeUserRole(new ChangeUserRoleCommand(
                userId,
                req.newRole(),
                requestingUserId,
                requestingUserRole
        ));

        return ResponseEntity.ok(ApiResponseDto.data(username));
    }
}
