package com.rzodeczko.infrastructure.event;

import com.rzodeczko.application.event.UserRegisteredEvent;
import com.rzodeczko.application.port.EmailPort;
import com.rzodeczko.domain.exception.UserNotFoundException;
import com.rzodeczko.domain.model.Email;
import com.rzodeczko.domain.model.Role;
import com.rzodeczko.domain.model.User;
import com.rzodeczko.domain.model.Username;
import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.domain.repository.UserRepository;
import com.rzodeczko.domain.repository.VerificationCodeRepository;
import com.rzodeczko.infrastructure.configuration.properties.UserActivationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationEventListenerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationCodeRepository verificationCodeRepository;

    @Mock
    private EmailPort emailPort;

    @Captor
    private ArgumentCaptor<VerificationCode> verificationCodeCaptor;

    @Captor
    private ArgumentCaptor<String> emailBodyCaptor;

    private UserRegistrationEventListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UserActivationProperties properties = new UserActivationProperties(300_000L, 6);

    @BeforeEach
    void setUp() {
        listener = new UserRegistrationEventListener(
                userRepository, verificationCodeRepository, emailPort, properties);
    }

    private User createUser() {
        return new User(userId, new Username("johndoe"), new Email("john@example.com"), "encoded-pw",
                Role.USER, false, null, null);
    }

    @Nested
    @DisplayName("onUserRegistered")
    class OnUserRegistered {

        @Test
        @DisplayName("should find user, save verification code, and send email")
        void shouldFindUserSaveCodeAndSendEmail() {
            // given
            var user = createUser();
            var event = new UserRegisteredEvent(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            listener.onUserRegistered(event);

            // then
            verify(userRepository).findById(userId);
            verify(verificationCodeRepository).save(any(VerificationCode.class));
            verify(emailPort).send(eq("john@example.com"), eq("Activation Code"), anyString());
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            // given
            var event = new UserRegisteredEvent(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> listener.onUserRegistered(event))
                    .isInstanceOf(UserNotFoundException.class);

            verify(verificationCodeRepository, never()).save(any());
            verify(emailPort, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("generated code should have correct number of digits")
        void generatedCodeShouldHaveCorrectDigits() {
            // given
            var user = createUser();
            var event = new UserRegisteredEvent(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            listener.onUserRegistered(event);

            // then
            verify(verificationCodeRepository).save(verificationCodeCaptor.capture());
            var savedCode = verificationCodeCaptor.getValue();
            assertThat(savedCode.getCode()).hasSize(properties.codeDigits());
            assertThat(savedCode.getCode()).matches("\\d{" + properties.codeDigits() + "}");
        }

        @Test
        @DisplayName("email should contain the code and expiration time")
        void emailShouldContainCodeAndExpiration() {
            // given
            var user = createUser();
            var event = new UserRegisteredEvent(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            listener.onUserRegistered(event);

            // then
            verify(verificationCodeRepository).save(verificationCodeCaptor.capture());
            var code = verificationCodeCaptor.getValue().getCode();

            verify(emailPort).send(eq("john@example.com"), eq("Activation Code"), emailBodyCaptor.capture());
            var body = emailBodyCaptor.getValue();
            assertThat(body).contains(code);
            assertThat(body).contains(String.valueOf(properties.expirationMs() / 1000));
        }

        @Test
        @DisplayName("verification code expiration should be in the future")
        void verificationCodeExpirationShouldBeInFuture() {
            // given
            var user = createUser();
            var event = new UserRegisteredEvent(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            long beforeCall = System.currentTimeMillis();

            // when
            listener.onUserRegistered(event);

            // then
            verify(verificationCodeRepository).save(verificationCodeCaptor.capture());
            var savedCode = verificationCodeCaptor.getValue();
            assertThat(savedCode.getExpiresAt()).isGreaterThan(beforeCall);
            assertThat(savedCode.getExpiresAt()).isGreaterThanOrEqualTo(beforeCall + properties.expirationMs());
        }
    }
}
