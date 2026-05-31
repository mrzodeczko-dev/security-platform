package com.rzodeczko.application.dto;

import java.util.UUID;

// Wynik weryfikacji poświadczeń — data transfer z UserServiceImpl do auth-service.
// Zawiera dane potrzebne auth-service do podjęcia decyzji po sprawdzeniu hasła.
//
// role jako String — izolacja między serwisami.
// Gdybyśmy użyli enum Role:
//   - auth-service musiałby importować ten sam enum
//   - coupling między JAR-ami: zmiana Role w user-service wymusza rebuild auth-service
// String "ROLE_USER" to stabilny kontrakt textowy — zero coupling.
//
// mfaRequired: true → auth-service nie generuje tokenów, czeka na TOTP code
//              false → auth-service generuje tokeny od razu
public record UserCredentialsResultDto(
        UUID userId,
        String username,
        String role,
        boolean mfaRequired
) {
}
