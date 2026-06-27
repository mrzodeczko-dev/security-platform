package com.rzodeczko.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetPermissionRequestDto(
        @NotBlank(message = "Code cannot be blank")
        String code
) {
}
