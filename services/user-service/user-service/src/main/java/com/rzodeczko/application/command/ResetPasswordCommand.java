package com.rzodeczko.application.command;

public record ResetPasswordCommand(
        String resetToken,
        String password,
        String passwordConfirmation
) {
}
