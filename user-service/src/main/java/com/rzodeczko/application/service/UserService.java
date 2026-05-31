package com.rzodeczko.application.service;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;

public interface UserService {

    // ----------------------------------------------------------------------------------------------------
    // Metody publiczne (dostepne przez api-gateway)
    // ----------------------------------------------------------------------------------------------------

    // Rejestracja: walidacja → hash hasła → INSERT → email z kodem aktywacyjnym
    String register(RegisterUserCommand command);

    // Aktywacja: weryfikacja kodu → UPDATE enabled=true → DELETE kod
    String activate(String code);

    // Ponowne wysłanie kodu: DELETE stary kod → INSERT nowy → email
    String resendActivationCode(String email);

    // Krok 2/3 resetu hasła: weryfikacja kodu → zwraca email (do użycia w kroku 3)
    String getPasswordResetPermission(String code);

    // Krok 3/3 resetu hasła: weryfikacja haseł → UPDATE hasło
    String resetPassword(ResetPasswordCommand command);

    // Setup MFA: generowanie secret TOTP → zapisanie → zwraca QR URL
    String setupMfa(String username);

    // changeUserRole — zmiana roli przez administratora.
    // Wymaga ROLE_ADMIN w nagłówku X-User-Role (weryfikacja w service layer).
    // Zwraca username zmienionego usera jako potwierdzenie operacji.
    String changeUserRole(ChangeUserRoleCommand command);

    // ----------------------------------------------------------------------------------------------------
    // Metody prywatne (wylacznie przez auth-service)
    // ----------------------------------------------------------------------------------------------------

    // Weryfikacja hasła podczas logowania — zwraca dane do generowania JWT lub MFA flag
    UserCredentialsResultDto verifyCredentials(VerifyCredentialsCommand command);

    // Pobranie mfaSecret + danych usera — auth-service weryfikuje TOTP lokalnie
    MfaDataResultDto getMfaData(GetMfaDataCommand command);
}
