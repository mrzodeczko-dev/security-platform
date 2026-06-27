package com.rzodeczko.application.command;

public record VerifyMfaCommand(String mfaId, int code) {
}
