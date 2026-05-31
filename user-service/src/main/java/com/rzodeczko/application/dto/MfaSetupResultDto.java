package com.rzodeczko.application.dto;

// Wynik generowania TOTP credentials przez MfaSetupPort.
// Zwracany z GoogleAuthMfaSetupAdapter → UserServiceImpl → UserController.
//
// secret: Base32-encoded TOTP secret — zapisywany w bazie przez UserServiceImpl.
// qrUrl: URL otpauth://totp/<issuer>:<username>?secret=<secret>&issuer=<issuer>
//        frontend zamienia na QR kod → user skanuje w Google Authenticator.
//
// Dlaczego ten DTO jest w application layer (nie domain)?
// Jest to wynik operacji infrastrukturalnej (generowanie kluczy kryptograficznych)
// przekazywany przez port do application layer. Domain nie generuje kluczy —
// to odpowiedzialność infrastruktury (GoogleAuthMfaSetupAdapter).
public record MfaSetupResultDto(String secret, String qrUrl) {
}
