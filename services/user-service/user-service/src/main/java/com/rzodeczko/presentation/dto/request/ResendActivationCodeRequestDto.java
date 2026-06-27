package com.rzodeczko.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendActivationCodeRequestDto(
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email must be a valid email address")
        String email
) {
}
