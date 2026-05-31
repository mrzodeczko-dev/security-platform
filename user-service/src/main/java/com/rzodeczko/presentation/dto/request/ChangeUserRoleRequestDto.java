package com.rzodeczko.presentation.dto.request;

import com.rzodeczko.domain.model.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequestDto(
        @NotNull(message = "New role cannot be null")
        Role newRole
) {
}
