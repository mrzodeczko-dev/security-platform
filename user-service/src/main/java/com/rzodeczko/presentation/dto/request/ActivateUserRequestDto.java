package com.rzodeczko.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ActivateUserRequestDto(
        @NotBlank(message = "Code cannot be blank")
        String code
) {
}
