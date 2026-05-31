package com.rzodeczko.application.command;

public record ResetPasswordCommand(
        String email,
        String password,
        String passwordConfirmation
) {
}
