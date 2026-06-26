package com.rzodeczko.application.service;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;

import java.util.UUID;

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

    // Step 2/3 password reset: code verification → generates one-time reset token (UUID, 5 min TTL)
    String getPasswordResetPermission(String code);

    // Step 3/3 password reset: reset token verification → UPDATE password → DELETE token
    String resetPassword(ResetPasswordCommand command);

    // MFA Setup: generate TOTP secret → save → return QR URL
    String setupMfa(UUID userId);

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
