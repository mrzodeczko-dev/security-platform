package com.rzodeczko.application.dto;


public record MfaSetupResultDto(String secret, String qrUrl) {
}
