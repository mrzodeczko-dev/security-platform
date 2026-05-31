package com.rzodeczko.application.service;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;

public interface UserService {

    // ----------------------------------------------------------------------------------------------------
    // Metody publiczne (dostepne przez api-gateway)
    // ----------------------------------------------------------------------------------------------------

    String register(RegisterUserCommand command);

    String activate(String code);

    String resendActivationCode(String email);

    String getPasswordResetPermission(String code);

    String resetPassword(ResetPasswordCommand command);

    String setupMfa(String username);

    String changeUserRole(ChangeUserRoleCommand command);

    // ----------------------------------------------------------------------------------------------------
    // Metody prywatne (wylacznie przez auth-service)
    // ----------------------------------------------------------------------------------------------------

    UserCredentialsResultDto verifyCredentials(VerifyCredentialsCommand command);

    MfaDataResultDto getMfaData(GetMfaDataCommand command);
}
