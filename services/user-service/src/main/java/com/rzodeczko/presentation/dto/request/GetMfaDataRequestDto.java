package com.rzodeczko.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GetMfaDataRequestDto(
        @NotBlank(message = "Username cannot be blank")
        String username
) {
}
