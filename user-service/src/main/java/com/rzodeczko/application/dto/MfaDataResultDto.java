package com.rzodeczko.application.dto;

import java.util.UUID;

// Wynik getMfaData() — dane MFA zwracane auth-service przez /internal/users/mfa-data.
// mfaSecret: Base32-encoded TOTP secret przechowywany w bazie user-service.
// auth-service używa go lokalnie: googleAuthenticator.authorize(mfaSecret, code).
// Po weryfikacji auth-service ma userId i role do wygenerowania JWT — zero kolejnych HTTP calli.

// Kwestie bezpieczenstwa w kontekscie mfaSecret
// Obecny stan: dev-ready
// ->   Docker network isolation (port 8083 expose, nie ports)
// ->   X-Internal-Secret header (stałoczasowe porównanie)
// Gdybys chcial zapewnic ochrone produkcyjnie:
// ->   Mutual TLS między auth-service a user-service
// ->   Cache w auth-service (Caffeine TTL=60s) minimalizuje liczbę transferów sekretu
// Dlaczego nasze rozwiazanie moze byc wystarczajace?
// ->   mfaSecret to klucz TOTP, nie hasło — sam w sobie nie daje dostępu do konta.
// ->   Atakujący potrzebuje RÓWNIEŻ hasła (Argon2, nigdy nie transferowanego plaintext).
public record MfaDataResultDto(
        UUID userId,
        String username,
        String role,
        String mfaSecret
) {
}
