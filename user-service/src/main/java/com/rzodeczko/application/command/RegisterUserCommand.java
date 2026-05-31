package com.rzodeczko.application.command;

import com.rzodeczko.domain.model.Role;

public record RegisterUserCommand(
        String username,
        String email,
        String password,
        String passwordConfirmation,
        Role role
) {
}
