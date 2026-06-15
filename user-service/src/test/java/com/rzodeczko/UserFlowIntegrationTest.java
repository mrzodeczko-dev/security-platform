package com.rzodeczko;

import com.rzodeczko.application.command.RegisterUserCommand;
import com.rzodeczko.application.command.ResetPasswordCommand;
import com.rzodeczko.application.command.VerifyCredentialsCommand;
import com.rzodeczko.application.service.UserService;
import com.rzodeczko.domain.model.Role;
import com.rzodeczko.domain.repository.VerificationCodeRepository;
import com.rzodeczko.infrastructure.persistence.entity.VerificationCodeEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaUserRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaVerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@DisplayName("User flow integration tests")
class UserFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("transactionalUserService")
    private UserService userService;

    @Autowired
    private JpaUserRepository jpaUserRepository;

    @Autowired
    private JpaVerificationCodeRepository jpaVerificationCodeRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanDb() {
        jpaVerificationCodeRepository.deleteAll();
        jpaUserRepository.deleteAll();
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    // ========================================
    // register → activate → verifyCredentials
    // ========================================
    @Nested
    @DisplayName("Full registration flow")
    class FullRegistrationFlow {

        @Test
        @DisplayName("register → activate → verifyCredentials should succeed end to end")
        void fullFlow_shouldSucceed() {
            // --- register ---
            var registerCmd = new RegisterUserCommand(
                    "integrationUser", "integration@test.com", "P@ssw0rd!", "P@ssw0rd!", Role.USER);

            String registeredUsername = userService.register(registerCmd);
            assertThat(registeredUsername).isEqualTo("integrationUser");

            // user should exist in DB but be disabled
            var userEntity = jpaUserRepository.findByUsername("integrationUser");
            assertThat(userEntity).isPresent();
            assertThat(userEntity.get().isEnabled()).isFalse();

            // wait for async event listener to create verification code
            AtomicReference<VerificationCodeEntity> verificationCodeRef = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() ->
                            {
                                Optional<VerificationCodeEntity> verificationCodeOpt = jpaVerificationCodeRepository.findByUserId(userEntity.get().getId());
                                assertThat(verificationCodeOpt).isPresent();
                                verificationCodeRef.set(verificationCodeOpt.get());
                            }
                    );

            String activationCode = verificationCodeRef.get().getCode();

            // --- activate ---
            String activatedUsername = userService.activate(activationCode);
            assertThat(activatedUsername).isEqualTo("integrationUser");

            // user should now be enabled
            var activatedUser = jpaUserRepository.findByUsername("integrationUser");
            assertThat(activatedUser).isPresent();
            assertThat(activatedUser.get().isEnabled()).isTrue();

            // verification code should be deleted
            var deletedVc = jpaVerificationCodeRepository.findByUserId(userEntity.get().getId());
            assertThat(deletedVc).isEmpty();

            // --- verifyCredentials ---
            var verifyCmd = new VerifyCredentialsCommand("integrationUser", "P@ssw0rd!");
            var credentials = userService.verifyCredentials(verifyCmd);

            assertThat(credentials.username()).isEqualTo("integrationUser");
            assertThat(credentials.role()).isEqualTo("ROLE_USER");
            assertThat(credentials.mfaRequired()).isFalse();
            assertThat(credentials.userId()).isEqualTo(userEntity.get().getId());
        }
    }

    // ========================================
    // register -> activate -> resetPassword -> verify with new password
    // ========================================
    @Nested
    @DisplayName("Password reset flow")
    class PasswordResetFlow {

        @Test
        @DisplayName("should reset password and authenticate with new one")
        void resetPassword_shouldAllowLoginWithNewPassword() throws Exception {
            // register + activate
            var registerCmd = new RegisterUserCommand(
                    "resetUser", "reset@test.com", "OldP@ss1", "OldP@ss1", Role.USER);
            userService.register(registerCmd);

            Thread.sleep(500);

            var userEntity = jpaUserRepository.findByUsername("resetUser").orElseThrow();
            var vcEntity = jpaVerificationCodeRepository.findByUserId(userEntity.getId()).orElseThrow();
            userService.activate(vcEntity.getCode());

            // reset password
            var resetCmd = new ResetPasswordCommand("reset@test.com", "NewP@ss2", "NewP@ss2");
            String resetUsername = userService.resetPassword(resetCmd);
            assertThat(resetUsername).isEqualTo("resetUser");

            // old password should fail
            assertThatThrownBy(() ->
                    userService.verifyCredentials(new VerifyCredentialsCommand("resetUser", "OldP@ss1")))
                    .isInstanceOf(com.rzodeczko.domain.exception.InvalidCredentialsException.class);

            // new password should work
            var credentials = userService.verifyCredentials(
                    new VerifyCredentialsCommand("resetUser", "NewP@ss2"));
            assertThat(credentials.username()).isEqualTo("resetUser");
        }
    }

    // ========================================
    // register -> activate -> setupMfa -> verifyCredentials (mfaRequired=true)
    // ========================================
    @Nested
    @DisplayName("MFA setup flow")
    class MfaSetupFlow {

        @Test
        @DisplayName("should enable MFA and return mfaRequired=true on verify")
        void setupMfa_shouldReturnMfaRequired() throws Exception {
            // register + activate
            var registerCmd = new RegisterUserCommand(
                    "mfaUser", "mfa@test.com", "P@ssw0rd!", "P@ssw0rd!", Role.USER);
            userService.register(registerCmd);

            Thread.sleep(500);

            var userEntity = jpaUserRepository.findByUsername("mfaUser").orElseThrow();
            var vcEntity = jpaVerificationCodeRepository.findByUserId(userEntity.getId()).orElseThrow();
            userService.activate(vcEntity.getCode());

            // setup MFA
            String qrUrl = userService.setupMfa(userEntity.getId());
            assertThat(qrUrl).isNotBlank();
            assertThat(qrUrl).contains("mfaUser");

            // verify credentials should now require MFA
            var credentials = userService.verifyCredentials(
                    new VerifyCredentialsCommand("mfaUser", "P@ssw0rd!"));
            assertThat(credentials.mfaRequired()).isTrue();
        }
    }

    // ========================================
    // register → activate → changeUserRole
    // ========================================
    @Nested
    @DisplayName("Role change flow")
    class RoleChangeFlow {

        @Test
        @DisplayName("admin should be able to promote user to admin")
        void adminShouldChangeUserRole() throws Exception {
            // register admin
            var adminCmd = new RegisterUserCommand(
                    "adminUser", "admin@test.com", "AdminP@ss1", "AdminP@ss1", Role.ADMIN);
            userService.register(adminCmd);

            Thread.sleep(500);

            var adminEntity = jpaUserRepository.findByUsername("adminUser").orElseThrow();
            var adminVc = jpaVerificationCodeRepository.findByUserId(adminEntity.getId()).orElseThrow();
            userService.activate(adminVc.getCode());

            // register regular user
            var userCmd = new RegisterUserCommand(
                    "regularUser", "regular@test.com", "UserP@ss1", "UserP@ss1", Role.USER);
            userService.register(userCmd);

            Thread.sleep(500);

            var userEntity = jpaUserRepository.findByUsername("regularUser").orElseThrow();
            var userVc = jpaVerificationCodeRepository.findByUserId(userEntity.getId()).orElseThrow();
            userService.activate(userVc.getCode());

            // admin changes user's role
            var changeCmd = new com.rzodeczko.application.command.ChangeUserRoleCommand(
                    userEntity.getId(), Role.ADMIN, adminEntity.getId(), "ROLE_ADMIN");
            String changedUsername = userService.changeUserRole(changeCmd);
            assertThat(changedUsername).isEqualTo("regularUser");

            // verify role changed
            var updatedUser = jpaUserRepository.findByUsername("regularUser").orElseThrow();
            assertThat(updatedUser.getRole()).isEqualTo(Role.ADMIN);
        }
    }

    // ========================================
    // Duplicate registration guard
    // ========================================
    @Nested
    @DisplayName("Duplicate registration")
    class DuplicateRegistration {

        @Test
        @DisplayName("should reject duplicate username")
        void shouldRejectDuplicateUsername() {
            var cmd1 = new RegisterUserCommand(
                    "dupeUser", "dupe1@test.com", "P@ss1", "P@ss1", Role.USER);
            userService.register(cmd1);

            var cmd2 = new RegisterUserCommand(
                    "dupeUser", "dupe2@test.com", "P@ss1", "P@ss1", Role.USER);

            assertThatThrownBy(() -> userService.register(cmd2))
                    .isInstanceOf(com.rzodeczko.domain.exception.UsernameAlreadyExistsException.class);
        }

        @Test
        @DisplayName("should reject duplicate email")
        void shouldRejectDuplicateEmail() {
            var cmd1 = new RegisterUserCommand(
                    "emailUser1", "same@test.com", "P@ss1", "P@ss1", Role.USER);
            userService.register(cmd1);

            var cmd2 = new RegisterUserCommand(
                    "emailUser2", "same@test.com", "P@ss1", "P@ss1", Role.USER);

            assertThatThrownBy(() -> userService.register(cmd2))
                    .isInstanceOf(com.rzodeczko.domain.exception.EmailAlreadyExistsException.class);
        }
    }

    // ========================================
    // Inactive account guard
    // ========================================
    @Nested
    @DisplayName("Inactive account")
    class InactiveAccount {

        @Test
        @DisplayName("verifyCredentials should reject inactive account")
        void shouldRejectInactiveAccount() {
            var cmd = new RegisterUserCommand(
                    "inactiveUser", "inactive@test.com", "P@ss1", "P@ss1", Role.USER);
            userService.register(cmd);

            assertThatThrownBy(() ->
                    userService.verifyCredentials(new VerifyCredentialsCommand("inactiveUser", "P@ss1")))
                    .isInstanceOf(com.rzodeczko.domain.exception.UserNotActivatedException.class);
        }
    }
}
