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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class,})
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
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
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            var savedUser = createDisabledUser();

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            String result = userService.register(command);

            assertThat(result).isEqualTo(USERNAME);
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publishUserRegistered(any(UserRegisteredEvent.class));
        }

        @Test
        @DisplayName("should throw when username already exists")
        void shouldThrow_whenUsernameExists() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(createEnabledUser()));

            assertThatThrownBy(() -> userService.register(command))
                    .isInstanceOf(UsernameAlreadyExistsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when email already exists")
        void shouldThrow_whenEmailExists() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(createEnabledUser()));

            assertThatThrownBy(() -> userService.register(command))
                    .isInstanceOf(EmailAlreadyExistsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when passwords do not match")
        void shouldThrow_whenPasswordsMismatch() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, "otherPassword");
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.register(command))
                    .isInstanceOf(PasswordMismatchException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should encode password before saving")
        void shouldEncodePassword_beforeSaving() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(createDisabledUser());

            userService.register(command);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo(ENCODED_PASSWORD);
        }

        @Test
        @DisplayName("should always create user with USER role")
        void shouldCreateUser_withUserRole() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            var savedUser = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, false, null, null);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            userService.register(command);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("should create user as disabled")
        void shouldCreateUser_asDisabled() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(createDisabledUser());

            userService.register(command);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should publish event with saved user ID")
        void shouldPublishEvent_withSavedUserId() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, PASSWORD);
            var savedUser = createDisabledUser();

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            userService.register(command);

            var captor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
            verify(eventPublisher).publishUserRegistered(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("should not publish event when validation fails")
        void shouldNotPublishEvent_whenValidationFails() {
            var command = new RegisterUserCommand(USERNAME, EMAIL, PASSWORD, "other");
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.register(command))
                    .isInstanceOf(PasswordMismatchException.class);
            verify(eventPublisher, never()).publishUserRegistered(any());
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

            assertThat(result).isEqualTo(USERNAME);
            assertThat(user.isEnabled()).isTrue();
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when code not found")
        void shouldThrow_whenCodeNotFound() {
            when(verificationCodeRepository.findByCode("bad-code")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.activate("bad-code"))
                    .isInstanceOf(VerificationCodeNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when code is expired")
        void shouldThrow_whenCodeExpired() {
            String code = "expired-code";
            long pastExpiry = Instant.now().toEpochMilli() - 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, pastExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.activate(code))
                    .isInstanceOf(VerificationCodeExpiredException.class);
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

            assertThatThrownBy(() -> userService.activate(code))
                    .isInstanceOf(UserAlreadyActivatedException.class);
        }

        @Test
        @DisplayName("should throw when user not found for valid code")
        void shouldThrow_whenUserNotFoundForValidCode() {
            String code = "valid-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.activate(code))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should save activated user")
        void shouldSaveActivatedUser() {
            String code = "valid-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);
            var user = createDisabledUser();

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            userService.activate(code);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should delete verification code after successful activation")
        void shouldDeleteCode_afterSuccessfulActivation() {
            String code = "valid-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);
            var user = createDisabledUser();

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            userService.activate(code);

            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should delete verification code when user already activated")
        void shouldDeleteCode_whenUserAlreadyActivated() {
            String code = "valid-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createEnabledUser()));

            assertThatThrownBy(() -> userService.activate(code))
                    .isInstanceOf(UserAlreadyActivatedException.class);
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should not save user when code is expired")
        void shouldNotSaveUser_whenCodeExpired() {
            String code = "expired-code";
            long pastExpiry = Instant.now().toEpochMilli() - 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, pastExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.activate(code))
                    .isInstanceOf(VerificationCodeExpiredException.class);
            verify(userRepository, never()).save(any());
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

            assertThat(result)
                    .extracting(
                            UserCredentialsResultDto::userId,
                            UserCredentialsResultDto::username,
                            UserCredentialsResultDto::role,
                            UserCredentialsResultDto::mfaRequired
                    )
                    .containsExactly(USER_ID, USERNAME, "ROLE_USER", false);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrow_whenUserNotFound() {
            var command = new VerifyCredentialsCommand("ghost", PASSWORD);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.verifyCredentials(command))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("should throw when account is not activated")
        void shouldThrow_whenAccountNotActivated() {
            var command = new VerifyCredentialsCommand(USERNAME, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(createDisabledUser()));

            assertThatThrownBy(() -> userService.verifyCredentials(command))
                    .isInstanceOf(UserNotActivatedException.class);
        }

        @Test
        @DisplayName("should throw when password is wrong")
        void shouldThrow_whenPasswordWrong() {
            var command = new VerifyCredentialsCommand(USERNAME, "wrongPass");
            var user = createEnabledUser();

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> userService.verifyCredentials(command))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("should return mfaRequired true when user has MFA active")
        void shouldReturnMfaRequired_whenMfaActive() {
            var user = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, true, "secret", "qrUrl");
            var command = new VerifyCredentialsCommand(USERNAME, PASSWORD);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            UserCredentialsResultDto result = userService.verifyCredentials(command);

            assertThat(result.mfaRequired()).isTrue();
        }

        @Test
        @DisplayName("should return correct role for admin user")
        void shouldReturnCorrectRole_forAdmin() {
            var admin = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.ADMIN, true, null, null);
            var command = new VerifyCredentialsCommand(USERNAME, PASSWORD);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            UserCredentialsResultDto result = userService.verifyCredentials(command);

            assertThat(result.role()).isEqualTo("ROLE_ADMIN");
        }
    }

    // ========================================
    // resetPassword
    // ========================================
    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        private static final String RESET_TOKEN = "reset-token-uuid";

        private VerificationCode createValidResetToken() {
            return new VerificationCode(UUID.randomUUID(), RESET_TOKEN,
                    Instant.now().toEpochMilli() + 300_000, USER_ID);
        }

        private VerificationCode createExpiredResetToken() {
            return new VerificationCode(UUID.randomUUID(), RESET_TOKEN,
                    Instant.now().toEpochMilli() - 1000, USER_ID);
        }

        @Test
        @DisplayName("should reset password when token and data are valid")
        void shouldResetPassword_whenValid() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "newPass", "newPass");
            var user = createEnabledUser();
            var vc = createValidResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPass")).thenReturn("$encoded$newPass");
            when(userRepository.save(any(User.class))).thenReturn(user);

            String result = userService.resetPassword(command);

            assertThat(result).isEqualTo(USERNAME);
            verify(passwordEncoder).encode("newPass");
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when reset token not found")
        void shouldThrow_whenTokenNotFound() {
            var command = new ResetPasswordCommand("invalid-token", "newPass", "newPass");
            when(verificationCodeRepository.findByCode("invalid-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.resetPassword(command))
                    .isInstanceOf(VerificationCodeNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when reset token is expired")
        void shouldThrow_whenTokenExpired() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "newPass", "newPass");
            var vc = createExpiredResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.resetPassword(command))
                    .isInstanceOf(VerificationCodeExpiredException.class);
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when passwords do not match")
        void shouldThrow_whenPasswordsMismatch() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "pass1", "pass2");
            var vc = createValidResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.resetPassword(command))
                    .isInstanceOf(PasswordMismatchException.class);
        }

        @Test
        @DisplayName("should throw when account is not activated")
        void shouldThrow_whenAccountNotActivated() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "newPass", "newPass");
            var vc = createValidResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createDisabledUser()));

            assertThatThrownBy(() -> userService.resetPassword(command))
                    .isInstanceOf(UserNotActivatedException.class);
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when user not found by token userId")
        void shouldThrow_whenUserNotFound() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "newPass", "newPass");
            var vc = createValidResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.resetPassword(command))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should update password with encoded value")
        void shouldUpdatePassword_withEncodedValue() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "newPass", "newPass");
            var user = createEnabledUser();
            var vc = createValidResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPass")).thenReturn("$encoded$newPass");
            when(userRepository.save(any(User.class))).thenReturn(user);

            userService.resetPassword(command);

            assertThat(user.getPassword()).isEqualTo("$encoded$newPass");
        }

        @Test
        @DisplayName("should not encode password when passwords mismatch")
        void shouldNotEncodePassword_whenPasswordsMismatch() {
            var command = new ResetPasswordCommand(RESET_TOKEN, "pass1", "pass2");
            var vc = createValidResetToken();

            when(verificationCodeRepository.findByCode(RESET_TOKEN)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.resetPassword(command))
                    .isInstanceOf(PasswordMismatchException.class);
            verify(passwordEncoder, never()).encode(any());
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

            assertThat(result).isEqualTo("target");
        }

        @Test
        @DisplayName("should throw when requesting user ID is null")
        void shouldThrow_whenRequestingUserIdNull() {
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, null, "ROLE_ADMIN");

            assertThatThrownBy(() -> userService.changeUserRole(command))
                    .isInstanceOf(InsufficientRoleException.class);
        }

        @Test
        @DisplayName("should throw when requesting user not found in database")
        void shouldThrow_whenRequestingUserNotFoundInDb() {
            UUID adminId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, adminId, "ROLE_ADMIN");

            when(userRepository.findById(adminId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeUserRole(command))
                    .isInstanceOf(InsufficientRoleException.class);
        }

        @Test
        @DisplayName("should throw when requesting user is not admin in database")
        void shouldThrow_whenRequestingUserNotAdminInDb() {
            UUID userId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, userId, "ROLE_ADMIN");
            var notAdmin = new User(userId, "user", "u@x.com", ENCODED_PASSWORD, Role.USER, true, null, null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(notAdmin));

            assertThatThrownBy(() -> userService.changeUserRole(command))
                    .isInstanceOf(InsufficientRoleException.class);
        }

        @Test
        @DisplayName("should throw RoleMismatchException when header role differs from DB role")
        void shouldThrow_whenHeaderRoleDiffersFromDbRole() {
            UUID adminId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(UUID.randomUUID(), Role.ADMIN, adminId, "ROLE_USER");
            var admin = new User(adminId, "admin", "admin@x.com", ENCODED_PASSWORD, Role.ADMIN, true, null, null);

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> userService.changeUserRole(command))
                    .isInstanceOf(RoleMismatchException.class)
                    .hasMessageContaining("ROLE_USER")
                    .hasMessageContaining("ROLE_ADMIN");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when target user not found")
        void shouldThrow_whenTargetUserNotFound() {
            UUID adminId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(targetId, Role.ADMIN, adminId, "ROLE_ADMIN");
            var admin = new User(adminId, "admin", "admin@x.com", ENCODED_PASSWORD, Role.ADMIN, true, null, null);

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(targetId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeUserRole(command))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should actually change role on target user")
        void shouldActuallyChangeRole_onTargetUser() {
            UUID adminId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            var command = new ChangeUserRoleCommand(targetId, Role.ADMIN, adminId, "ROLE_ADMIN");
            var admin = new User(adminId, "admin", "admin@x.com", ENCODED_PASSWORD, Role.ADMIN, true, null, null);
            var target = new User(targetId, "target", "target@x.com", ENCODED_PASSWORD, Role.USER, true, null, null);

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(userRepository.save(any(User.class))).thenReturn(target);

            userService.changeUserRole(command);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
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

            assertThat(result).isEqualTo(qrUrl);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should throw when MFA is already active")
        void shouldThrow_whenMfaAlreadyActive() {
            var user = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, true, "secret", "qrUrl");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.setupMfa(USER_ID))
                    .isInstanceOf(MfaAlreadyActivatedException.class);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrow_whenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.setupMfa(USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should save MFA secret and QR URL on user")
        void shouldSaveMfaSecretAndQrUrl_onUser() {
            var user = createEnabledUser();
            String secret = "TOTP_SECRET";
            String qrUrl = "otpauth://totp/App:testuser?secret=TOTP_SECRET";

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(mfaSetup.generateCredentials(USERNAME)).thenReturn(new MfaSetupResultDto(secret, qrUrl));
            when(userRepository.save(any(User.class))).thenReturn(user);

            userService.setupMfa(USER_ID);

            var captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getMfaSecret()).isEqualTo(secret);
            assertThat(captor.getValue().getMfaQrUrl()).isEqualTo(qrUrl);
        }

        @Test
        @DisplayName("should not generate credentials when MFA already active")
        void shouldNotGenerateCredentials_whenMfaAlreadyActive() {
            var user = new User(USER_ID, USERNAME, EMAIL, ENCODED_PASSWORD, Role.USER, true, "secret", "qrUrl");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.setupMfa(USER_ID))
                    .isInstanceOf(MfaAlreadyActivatedException.class);
            verify(mfaSetup, never()).generateCredentials(any());
            verify(userRepository, never()).save(any());
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

            assertThat(result)
                    .extracting(
                            MfaDataResultDto::userId,
                            MfaDataResultDto::username,
                            MfaDataResultDto::role,
                            MfaDataResultDto::mfaSecret
                    )
                    .containsExactly(USER_ID, USERNAME, "ROLE_USER", "mfaSecret");
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrow_whenUserNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMfaData(new GetMfaDataCommand("ghost")))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should return null mfaSecret when user has no MFA")
        void shouldReturnNullMfaSecret_whenNoMfa() {
            var user = createEnabledUser(); // mfaSecret = null
            var command = new GetMfaDataCommand(USERNAME);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            MfaDataResultDto result = userService.getMfaData(command);

            assertThat(result.mfaSecret()).isNull();
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

            assertThat(result).isEqualTo(USERNAME);
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

            assertThatThrownBy(() -> userService.resendActivationCode("noone@x.com"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should publish event with correct user ID")
        void shouldPublishEvent_withCorrectUserId() {
            var user = createDisabledUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(verificationCodeRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            userService.resendActivationCode(EMAIL);

            var captor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
            verify(eventPublisher).publishUserRegistered(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("should not delete code when none exists")
        void shouldNotDeleteCode_whenNoneExists() {
            var user = createDisabledUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(verificationCodeRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            userService.resendActivationCode(EMAIL);

            verify(verificationCodeRepository, never()).delete(any());
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

            assertThat(result).isEqualTo(EMAIL);
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

            assertThatThrownBy(() -> userService.getPasswordResetPermission(code))
                    .isInstanceOf(UserNotActivatedException.class);
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when code not found")
        void shouldThrow_whenCodeNotFound() {
            when(verificationCodeRepository.findByCode("bad-code")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getPasswordResetPermission("bad-code"))
                    .isInstanceOf(VerificationCodeNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when code is expired")
        void shouldThrow_whenCodeExpired() {
            String code = "expired-code";
            long pastExpiry = Instant.now().toEpochMilli() - 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, pastExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.getPasswordResetPermission(code))
                    .isInstanceOf(VerificationCodeExpiredException.class);
            verify(verificationCodeRepository).delete(vc);
        }

        @Test
        @DisplayName("should throw when user not found for valid code")
        void shouldThrow_whenUserNotFoundForValidCode() {
            String code = "reset-code";
            long futureExpiry = Instant.now().toEpochMilli() + 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, futureExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getPasswordResetPermission(code))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("should delete code even when expired")
        void shouldDeleteCode_evenWhenExpired() {
            String code = "expired-code";
            long pastExpiry = Instant.now().toEpochMilli() - 600_000;
            var vc = new VerificationCode(UUID.randomUUID(), code, pastExpiry, USER_ID);

            when(verificationCodeRepository.findByCode(code)).thenReturn(Optional.of(vc));

            assertThatThrownBy(() -> userService.getPasswordResetPermission(code))
                    .isInstanceOf(VerificationCodeExpiredException.class);
            verify(verificationCodeRepository).delete(vc);
        }
    }
}
