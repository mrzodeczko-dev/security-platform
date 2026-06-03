package com.rzodeczko.application.command;

public record VerifyMfaCommand(String username, int code) {
}
