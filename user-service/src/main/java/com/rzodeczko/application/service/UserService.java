package com.rzodeczko.application.service;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;

public interface UserService {

    // ----------------------------------------------------------------------------------------------------
    // Public methods (available via api-gateway)
    // ----------------------------------------------------------------------------------------------------

    // Registration: validation → hash password → INSERT → email with activation code
    String register(RegisterUserCommand command);

    // Activation: code verification → UPDATE enabled=true → DELETE code
    String activate(String code);

    // Resend code: DELETE old code → INSERT new → email
    String resendActivationCode(String email);

    // Step 2/3 password reset: code verification → returns email (for use in step 3)
    String getPasswordResetPermission(String code);

    // Step 3/3 password reset: password verification → UPDATE password
    String resetPassword(ResetPasswordCommand command);

    // MFA Setup: generate TOTP secret → save → return QR URL
    String setupMfa(String username);

    // changeUserRole — role change by administrator.
    // Requires ROLE_ADMIN in X-User-Role header (verification in service layer).
    // Returns username of changed user as operation confirmation.
    String changeUserRole(ChangeUserRoleCommand command);

    // ----------------------------------------------------------------------------------------------------
    // Private methods (only via auth-service)
    // ----------------------------------------------------------------------------------------------------
    UserCredentialsResultDto verifyCredentials(VerifyCredentialsCommand command);

    MfaDataResultDto getMfaData(GetMfaDataCommand command);
}
