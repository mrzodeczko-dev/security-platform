package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.command.LoginCommand;
import com.rzodeczko.application.command.LogoutCommand;
import com.rzodeczko.application.command.RefreshTokenCommand;
import com.rzodeczko.application.command.VerifyMfaCommand;
import com.rzodeczko.application.service.AuthService;
import com.rzodeczko.presentation.dto.request.LoginRequestDto;
import com.rzodeczko.presentation.dto.request.VerifyMfaRequestDto;
import com.rzodeczko.presentation.dto.response.AccessTokenResponseDto;
import com.rzodeczko.presentation.dto.response.ApiResponseDto;
import com.rzodeczko.presentation.dto.response.LoginResponseDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto req,
            HttpServletResponse httpResponse
    ) {
        var result = authService.login(new LoginCommand(req.username(), req.password()));

        if (result.mfaRequired()) {
            return ResponseEntity.ok(ApiResponseDto.data(
                    new LoginResponseDto(true, result.mfaId(), result.usernameForMfa(), null)
            ));
        }

        addRefreshTokenCookie(httpResponse, result.tokens().refreshToken());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.data(new LoginResponseDto(
                        false, null, null, new AccessTokenResponseDto(result.tokens().accessToken())
                )));
    }

    @PostMapping("/mfa")
    public ResponseEntity<ApiResponseDto<AccessTokenResponseDto>> verifyMfa(
            @Valid @RequestBody VerifyMfaRequestDto req,
            HttpServletResponse httpResponse
    ) {
        var result = authService.verifyMfa(new VerifyMfaCommand(req.mfaId(), req.code()));
        addRefreshTokenCookie(httpResponse, result.tokens().refreshToken());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.data(new AccessTokenResponseDto(result.tokens().accessToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto<AccessTokenResponseDto>> refresh(
            @CookieValue(name = "refresh-token") String refreshToken,
            HttpServletResponse httpResponse
    ) {
        var tokens = authService.refresh(new RefreshTokenCommand(refreshToken));
        addRefreshTokenCookie(httpResponse, tokens.refreshToken());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.data(new AccessTokenResponseDto(tokens.accessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<String>> logout(
            @CookieValue(name = "refresh-token", required = false) String refreshToken,
            HttpServletResponse httpResponse
    ) {
        if (refreshToken != null) {
            authService.logout(new LogoutCommand(refreshToken));
        }
        Cookie cookie = new Cookie("refresh-token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(0);
        httpResponse.addCookie(cookie);
        return ResponseEntity.ok(ApiResponseDto.data("Logged out successfully"));
    }

    private void addRefreshTokenCookie(HttpServletResponse httpResponse, String refreshToken) {
        Cookie cookie = new Cookie("refresh-token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(3000);
        // cookie.setSecure(true); /*on prod with https*/
        httpResponse.addCookie(cookie);
    }
}
