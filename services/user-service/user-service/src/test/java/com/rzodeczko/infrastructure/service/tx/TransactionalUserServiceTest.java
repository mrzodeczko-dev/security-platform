package com.rzodeczko.infrastructure.service.tx;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;
import com.rzodeczko.application.service.UserService;
import com.rzodeczko.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalUserServiceTest {

    @Mock
    private UserService delegate;

    @InjectMocks
    private TransactionalUserService transactionalUserService;

    // --- register ---

    @Test
    @DisplayName("register - delegates to underlying service and returns result")
    void register_delegatesToUnderlyingService() {
        var command = new RegisterUserCommand("john", "john@example.com", "pass123", "pass123");
        when(delegate.register(command)).thenReturn("User registered successfully");

        String result = transactionalUserService.register(command);

        assertThat(result).isEqualTo("User registered successfully");
        verify(delegate).register(command);
    }

    // --- activate ---

    @Test
    @DisplayName("activate - delegates to underlying service and returns result")
    void activate_delegatesToUnderlyingService() {
        String code = "activation-code-123";
        when(delegate.activate(code)).thenReturn("User activated successfully");

        String result = transactionalUserService.activate(code);

        assertThat(result).isEqualTo("User activated successfully");
        verify(delegate).activate(code);
    }

    // --- resendActivationCode ---

    @Test
    @DisplayName("resendActivationCode - delegates to underlying service and returns result")
    void resendActivationCode_delegatesToUnderlyingService() {
        String email = "john@example.com";
        when(delegate.resendActivationCode(email)).thenReturn("Activation code resent");

        String result = transactionalUserService.resendActivationCode(email);

        assertThat(result).isEqualTo("Activation code resent");
        verify(delegate).resendActivationCode(email);
    }

    // --- getPasswordResetPermission ---

    @Test
    @DisplayName("getPasswordResetPermission - delegates to underlying service and returns result")
    void getPasswordResetPermission_delegatesToUnderlyingService() {
        String code = "reset-code-456";
        when(delegate.getPasswordResetPermission(code)).thenReturn("john@example.com");

        String result = transactionalUserService.getPasswordResetPermission(code);

        assertThat(result).isEqualTo("john@example.com");
        verify(delegate).getPasswordResetPermission(code);
    }

    // --- resetPassword ---

    @Test
    @DisplayName("resetPassword - delegates to underlying service and returns result")
    void resetPassword_delegatesToUnderlyingService() {
        var command = new ResetPasswordCommand("reset-token-uuid", "newPass123", "newPass123");
        when(delegate.resetPassword(command)).thenReturn("Password reset successfully");

        String result = transactionalUserService.resetPassword(command);

        assertThat(result).isEqualTo("Password reset successfully");
        verify(delegate).resetPassword(command);
    }

    // --- setupMfa ---

    @Test
    @DisplayName("setupMfa - delegates to underlying service and returns result")
    void setupMfa_delegatesToUnderlyingService() {
        UUID userId = UUID.randomUUID();
        when(delegate.setupMfa(userId)).thenReturn("otpauth://totp/...");

        String result = transactionalUserService.setupMfa(userId);

        assertThat(result).isEqualTo("otpauth://totp/...");
        verify(delegate).setupMfa(userId);
    }

    // --- changeUserRole ---

    @Test
    @DisplayName("changeUserRole - delegates to underlying service and returns result")
    void changeUserRole_delegatesToUnderlyingService() {
        var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, UUID.randomUUID(), "ROLE_ADMIN");
        when(delegate.changeUserRole(command)).thenReturn("john");

        String result = transactionalUserService.changeUserRole(command);

        assertThat(result).isEqualTo("john");
        verify(delegate).changeUserRole(command);
    }

    // --- verifyCredentials ---

    @Test
    @DisplayName("verifyCredentials - delegates to underlying service and returns result")
    void verifyCredentials_delegatesToUnderlyingService() {
        var command = new VerifyCredentialsCommand("john", "pass123");
        var expectedResult = new UserCredentialsResultDto(UUID.randomUUID(), "john", "ROLE_USER", false);
        when(delegate.verifyCredentials(command)).thenReturn(expectedResult);

        UserCredentialsResultDto result = transactionalUserService.verifyCredentials(command);

        assertThat(result).isEqualTo(expectedResult);
        verify(delegate).verifyCredentials(command);
    }

    // --- getMfaData ---

    @Test
    @DisplayName("getMfaData - delegates to underlying service and returns result")
    void getMfaData_delegatesToUnderlyingService() {
        var command = new GetMfaDataCommand("john");
        var expectedResult = new MfaDataResultDto(UUID.randomUUID(), "john", "ROLE_USER", "JBSWY3DPEHPK3PXP");
        when(delegate.getMfaData(command)).thenReturn(expectedResult);

        MfaDataResultDto result = transactionalUserService.getMfaData(command);

        assertThat(result).isEqualTo(expectedResult);
        verify(delegate).getMfaData(command);
    }
}
