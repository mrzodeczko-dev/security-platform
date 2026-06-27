package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.command.GetMfaDataCommand;
import com.rzodeczko.application.command.VerifyCredentialsCommand;
import com.rzodeczko.application.service.UserService;
import com.rzodeczko.presentation.dto.request.GetMfaDataRequestDto;
import com.rzodeczko.presentation.dto.request.VerifyCredentialsRequestDto;
import com.rzodeczko.presentation.dto.response.MfaDataResponseDto;
import com.rzodeczko.presentation.dto.response.UserCredentialsResponseDto;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/users")
@Slf4j
public class InternalUserController {
    private final UserService userService;

    public InternalUserController(@Qualifier("transactionalUserService") UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/credentials")
    public ResponseEntity<UserCredentialsResponseDto> verifyCredentials(
            @Valid @RequestBody VerifyCredentialsRequestDto req
    ) {
        log.debug("Internal: verifyCredentials for username={}", req.username());
        var result = userService.verifyCredentials(
                new VerifyCredentialsCommand(req.username(), req.password())
        );
        return ResponseEntity.ok(new UserCredentialsResponseDto(
                result.userId(),
                result.username(),
                result.role(),
                result.mfaRequired()
        ));
    }

    @PostMapping("/mfa")
    public ResponseEntity<MfaDataResponseDto> getMfaData(
            @Valid @RequestBody GetMfaDataRequestDto req
    ) {
        log.debug("Internal: getMfaData for username={}", req.username());
        var result = userService.getMfaData(new GetMfaDataCommand(req.username()));
        return ResponseEntity.ok(new MfaDataResponseDto(
                result.userId(),
                result.username(),
                result.role(),
                result.mfaSecret()
        ));
    }
}
