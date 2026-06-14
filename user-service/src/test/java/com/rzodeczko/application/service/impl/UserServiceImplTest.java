package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.MfaSetupResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;
import com.rzodeczko.application.event.UserRegisteredEvent;
import com.rzodeczko.application.port.EventPublisherPort;
import com.rzodeczko.application.port.MfaSetupPort;
import com.rzodeczko.application.port.PasswordEncoderPort;
import com.rzodeczko.domain.exception.*;
import com.rzodeczko.domain.model.Role;
import com.rzodeczko.domain.model.User;
import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.domain.repository.UserRepository;
import com.rzodeczko.domain.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    @Mock
    private PasswordEncoderPort passwordEncoder;
    @Mock
    private EventPublisherPort eventPublisher;
    @Mock
    private MfaSetupPort mfaSetup;

    @InjectMocks
    private UserServiceImpl userService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USERNAME = "testuser";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "securePassword123";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";

    private User createEnabledUser() {
        return new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, true, null, null);
    }

    private User createDisabledUser() {
        return new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, false, null, null);
    }

    private User createAdminUser() {
        return new User(USER_ID, "admin", "admin@example.com", ENCODED_PASSWORD, Role.ADMIN, true, null, null);
    }

    // ========================================
    // register
    // ========================================
    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register user when data is valid")
        void shouldRegisterUser_whenDataValid() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD, Role.USER);
            var savedUser = createDisabledUser();

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            String result = userService.register(command);

            assertEquals(USERNAME, result);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publishUserRegistered(any(UserRegisteredEvent.class));
        }

        @Test
        @DisplayName("should throw when username already exists")
        void shouldThrow_whenUsernameExists() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD, Role.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(createEnabledUser()));

            assertThrows(UsernameAlreadyExistsException.class, () -> userService.register(command));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when email already exists")
        void shouldThrow_whenEmailExists() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD, Role.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(createEnabledUser()));

            assertThrows(EmailAlreadyExistsException.class, () -> userService.register(command));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when passwords do not match")
        void shouldThrow_whenPasswordsMismatch() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, "otherPassword", Role.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThrows(PasswordMismatchException.class, () -> userService.register(command));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should encode password before saving")
        void shouldEncodePassword_beforeSaving() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD, Role.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(createDisabledUser());

            userService.register(command);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals(ENCODED_PASSWORD, captor.getValue().getPassword());
        }
    }

    // ========================================
    // activate
    // ========================================
    @Nested
    @DisplayName("activate()")
    class ActivateTests {

        @Test
        @DisplayName("should activate user when code is valid")
        void shouldActivateUser_whenCodeValid() {
            String code = "valid-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);
            var user = createDisabledUser();

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            String result = userService.activate(code);

            assertEquals(USERNAME, result);
            assertTrue(user.isEnabled());
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when code not found")
        void shouldThrow_whenCodeNotFound() {
            when(verificationCodeRepository.findByCode("bad-code")).thenReturn(Optional.empty());

            assertThrows(VerificationCodeNotFoundException.class, () -> userService.activate("bad-code"));
        }

        @Test
        @DisplayName("should throw when code is expired")
        void shouldThrow_whenCodeExpired() {
            String code = "expired-code";
            long pastExpiry = Instant.now().toEpochMilli() - 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, pastExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));

            assertThrows(VerificationCodeExpiredException.class, () -> userService.activate(code));
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when user is already activated")
        void shouldThrow_whenUserAlreadyActivated() {
            String code = "valid-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);
            var user = createEnabledUser();

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThrows(UserAlreadyActivatedException.class, () -> userService.activate(code));
        }
    }

    // ========================================
    // verifyCredentials
    // ========================================
    @Nested
    @DisplayName("verifyCredentials()")
    class VerifyCredentialsTests {

        @Test
        @DisplayName("should return user credentials when valid")
        void shouldReturnCredentials_whenValid() {
            var command = new VerifyCredentialsCommand(USERNAME, PASSWORD);
            var user = createEnabledUser();

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            UserCredentialsResultDto result = userService.verifyCredentials(command);

            assertEquals(USER_ID, result.userId());
            assertEquals(USERNAME, result.username());
            assertEquals("ROLE_USER", result.role());
            assertFalse(result.mfaRequired());
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrow_whenUserNotFound() {
            var command = new VerifyCredentialsCommand("ghost", PASSWORD);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> userService.verifyCredentials(command));
        }

        @Test
        @DisplayName("should throw when account is not activated")
        void shouldThrow_whenAccountNotActivated() {
            var command = new VerifyCredentialsCommand(USERNAME, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(createDisabledUser()));

            assertThrows(UserNotActivatedException.class, () -> userService.verifyCredentials(command));
        }

        @Test
        @DisplayName("should throw when password is wrong")
        void shouldThrow_whenPasswordWrong() {
            var command = new VerifyCredentialsCommand(USERNAME, "wrongPass");
            var user = createEnabledUser();

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass", ENCODED_PASSWORD)).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> userService.verifyCredentials(command));
        }
    }

    // ========================================
    // resetPassword
    // ========================================
    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("should reset password when data is valid")
        void shouldResetPassword_whenValid() {
            var command = new ResetPasswordCommand(EMAIL, "newPass", "newPass");
            var user = createEnabledUser();

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPass")).thenReturn("$encoded$newPass");
            when(userRepository.save(any(User.class))).thenReturn(user);

            String result = userService.resetPassword(command);

            assertEquals(USERNAME, result);
            verify(passwordEncoder).encode("newPass");
        }

        @Test
        @DisplayName("should throw when passwords do not match")
        void shouldThrow_whenPasswordsMismatch() {
            var command = new ResetPasswordCommand(EMAIL, "pass1", "pass2");

            assertThrows(PasswordMismatchException.class, () -> userService.resetPassword(command));
        }

        @Test
        @DisplayName("should throw when account is not activated")
        void shouldThrow_whenAccountNotActivated() {
            var command = new ResetPasswordCommand(EMAIL, "newPass", "newPass");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(createDisabledUser()));

            assertThrows(UserNotActivatedException.class, () -> userService.resetPassword(command));
        }
    }

    // ========================================
    // changeUserRole
    // ========================================
    @Nested
    @DisplayName("changeUserRole()")
    class ChangeUserRoleTests {

        @Test
        @DisplayName("should change role when requester is admin")
        void shouldChangeRole_whenAdmin() {
            UUID adminId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(targetId, Role.ADMIN, adminId, "ROLE_ADMIN");
            var admin = new User(adminId, "admin", "admin@x.com", ENCODED_PASSWORD, Role.ADMIN, true, null, null);
            var target = new User(targetId, "target", "target@x.com", ENCODED_PASSWORD, Role.USER, true, null, null);

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(userRepository.save(any(User.class))).thenReturn(target);

            String result = userService.changeUserRole(command);

            assertEquals("target", result);
        }

        @Test
        @DisplayName("should throw when requesting user role is not ADMIN")
        void shouldThrow_whenNotAdminRole() {
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, UUID.randomUUID(), "ROLE_USER");

            assertThrows(InsufficientRoleException.class, () -> userService.changeUserRole(command));
        }

        @Test
        @DisplayName("should throw when requesting user ID is null")
        void shouldThrow_whenRequestingUserIdNull() {
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, null, "ROLE_ADMIN");

            assertThrows(InsufficientRoleException.class, () -> userService.changeUserRole(command));
        }

        @Test
        @DisplayName("should throw when requesting user is not admin in database")
        void shouldThrow_whenRequestingUserNotAdminInDb() {
            UUID userId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, userId, "ROLE_ADMIN");
            var notAdmin = new User(userId, "user", "u@x.com", ENCODED_PASSWORD, Role.USER, true, null, null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(notAdmin));

            assertThrows(InsufficientRoleException.class, () -> userService.changeUserRole(command));
        }
    }

    // ========================================
    // setupMfa
    // ========================================
    @Nested
    @DisplayName("setupMfa()")
    class SetupMfaTests {

        @Test
        @DisplayName("should setup MFA and return QR URL")
        void shouldSetupMfa_andReturnQrUrl() {
            var user = createEnabledUser();
            String qrUrl = "otpauth://totp/App:testuser?secret=ABC123";

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(mfaSetup.generateCredentials(USERNAME)).thenReturn(new MfaSetupResultDto("ABC123", qrUrl));
            when(userRepository.save(any(User.class))).thenReturn(user);

            String result = userService.setupMfa(USER_ID);

            assertEquals(qrUrl, result);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should throw when MFA is already active")
        void shouldThrow_whenMfaAlreadyActive() {
            var user = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, true, "secret", "qrUrl");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThrows(MfaAlreadyActivatedException.class, () -> userService.setupMfa(USER_ID));
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrow_whenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> userService.setupMfa(USER_ID));
        }
    }

    // ========================================
    // getMfaData
    // ========================================
    @Nested
    @DisplayName("getMfaData()")
    class GetMfaDataTests {

        @Test
        @DisplayName("should return MFA data")
        void shouldReturnMfaData() {
            var user = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, true, "mfaSecret", "qrUrl");
            var command = new GetMfaDataCommand(USERNAME);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            MfaDataResultDto result = userService.getMfaData(command);

            assertEquals(USER_ID, result.userId());
            assertEquals(USERNAME, result.username());
            assertEquals("ROLE_USER", result.role());
            assertEquals("mfaSecret", result.mfaSecret());
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrow_whenUserNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> userService.getMfaData(new GetMfaDataCommand("ghost")));
        }
    }

    // ========================================
    // resendActivationCode
    // ========================================
    @Nested
    @DisplayName("resendActivationCode()")
    class ResendActivationCodeTests {

        @Test
        @DisplayName("should resend activation code")
        void shouldResendCode() {
            var user = createDisabledUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(verificationCodeRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            String result = userService.resendActivationCode(EMAIL);

            assertEquals(USERNAME, result);
            verify(eventPublisher).publishUserRegistered(any(UserRegisteredEvent.class));
        }

        @Test
        @DisplayName("should delete old code before resending")
        void shouldDeleteOldCode_beforeResending() {
            var user = createDisabledUser();
            var oldVc = new VerificationCode(UUID.randomUUID(), "old-code", 0, USER_ID);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(verificationCodeRepository.findByUserId(USER_ID)).thenReturn(Optional.of(oldVc));

            userService.resendActivationCode(EMAIL);

            verify(verificationCodeRepository).delete(oldVc);
            verify(eventPublisher).publishUserRegistered(any(UserRegisteredEvent.class));
        }

        @Test
        @DisplayName("should throw when email not found")
        void shouldThrow_whenEmailNotFound() {
            when(userRepository.findByEmail("noone@x.com")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> userService.resendActivationCode("noone@x.com"));
        }
    }

    // ========================================
    // getPasswordResetPermission
    // ========================================
    @Nested
    @DisplayName("getPasswordResetPermission()")
    class GetPasswordResetPermissionTests {

        @Test
        @DisplayName("should return email when code is valid and account is active")
        void shouldReturnEmail_whenCodeValidAndAccountActive() {
            String code = "reset-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);
            var user = createEnabledUser();

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            String result = userService.getPasswordResetPermission(code);

            assertEquals(EMAIL, result);
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when account is not activated")
        void shouldThrow_whenAccountNotActivated() {
            String code = "reset-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createDisabledUser()));

            assertThrows(UserNotActivatedException.class, () -> userService.getPasswordResetPermission(code));
            verify(verificationCodeRepository).delete(vc);
        }
    }
}
