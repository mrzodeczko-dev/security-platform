package com.rzodeczko.application.command;

public record RegisterUserCommand(
        String username,
        String email,
        String password,
        String passwordConfirmation
) {
}
