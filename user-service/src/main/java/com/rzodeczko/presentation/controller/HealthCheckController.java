package com.rzodeczko.presentation.controller;

import com.rzodeczko.infrastructure.configuration.properties.InternalSecurityProperties;
import com.rzodeczko.infrastructure.configuration.properties.PasswordEncoderProperties;
import com.rzodeczko.infrastructure.configuration.properties.UserActivationProperties;
import com.rzodeczko.presentation.dto.HealthCheckResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthCheckController {
    private final InternalSecurityProperties internalSecurityProperties;
    private final PasswordEncoderProperties passwordEncoderProperties;
    private final UserActivationProperties userActivationProperties;

    @GetMapping("/")
    public ResponseEntity<HealthCheckResponseDto> healthCheck() {
        log.info("Internal Security: {}", internalSecurityProperties.secret());
        log.info("Password Encoder Type: {}", passwordEncoderProperties.encoder().type());
        log.info("User Activation Expiration Ms: {}", userActivationProperties.expirationMs());
        log.info("User Activation Code Digits: {}", userActivationProperties.codeDigits());
        return ResponseEntity.ok().body(new HealthCheckResponseDto("USER SERVICE OK"));
    }
}
