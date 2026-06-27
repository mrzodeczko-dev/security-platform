package com.rzodeczko.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VerifyMfaRequestDto(
        @NotBlank(message = "MFA ID cannot be blank")
        String mfaId,

        @Min(value = 100000, message = "Code must be a 6-digit number")
        @Max(value = 999999, message = "Code must be a 6-digit number")
        int code
) {
}
