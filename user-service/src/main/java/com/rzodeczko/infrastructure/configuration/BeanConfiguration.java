package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.application.port.EventPublisherPort;
import com.rzodeczko.application.port.MfaSetupPort;
import com.rzodeczko.application.port.PasswordEncoderPort;
import com.rzodeczko.application.service.UserService;
import com.rzodeczko.application.service.impl.UserServiceImpl;
import com.rzodeczko.domain.repository.UserRepository;
import com.rzodeczko.domain.repository.VerificationCodeRepository;
import com.rzodeczko.infrastructure.configuration.properties.InternalSecurityProperties;
import com.rzodeczko.infrastructure.configuration.properties.MfaProperties;
import com.rzodeczko.infrastructure.configuration.properties.PasswordEncoderProperties;
import com.rzodeczko.infrastructure.configuration.properties.UserActivationProperties;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
        InternalSecurityProperties.class,
        PasswordEncoderProperties.class,
        UserActivationProperties.class,
        MfaProperties.class
})
public class BeanConfiguration {

    // Singleton @Bean — thread-safe. Tworzenie jest kosztowne (konfiguracja algorytmu).
    // Argon2 parametry:
    // saltLength=16  → 128-bit salt per hash → rainbow tables bezskuteczne
    // hashLength=32  → 256-bit output
    // parallelism=1  → jeden wątek per hash (wystarczy)
    // memory=65536   → 64 MB RAM per hash → GPU attack bardzo drogi
    // iterations=10  → ~0.5s per hash na standardowym serwerze
    //
    // BCrypt jako fallback dla środowisk z ograniczoną pamięcią.
    // BCrypt max 72 bajty hasła, CPU-bound (mniej odporny na GPU niż Argon2).
    //
    // Ten bean jest używany przez PasswordEncoderAdapter,
    @Bean
    public PasswordEncoder passwordEncoder(PasswordEncoderProperties properties) {
        return switch (properties.encoder().type()) {
            case "argon2" -> new Argon2PasswordEncoder(16, 32, 1, 65536, 10);
            case "bcrypt" -> new BCryptPasswordEncoder();
            default -> throw new IllegalArgumentException(
                    "Unknown password encoder type: %s. Supported values: argon2, bcrypt".formatted(
                            properties.encoder().type()));
        };
    }

    // Singleton @Bean — thread-safe (bezstanowy, każde wywołanie niezależne).
    // Używany WYŁĄCZNIE przez GoogleAuthMfaSetupAdapter do setupMfa().
    // Weryfikacja TOTP jest w auth-service (osobna instancja GoogleAuthenticator).
    //
    // THREAD SAFETY: createCredentials() jest bezstanowe — równoległe wywołania
    // z tysiącami VT są bezpieczne. Wewnętrznie używa SecureRandom (thread-safe).
    @Bean
    public GoogleAuthenticator googleAuthenticator() {
        return new GoogleAuthenticator();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean("userServiceImpl")
    public UserService userServiceImpl(
            UserRepository userRepository,
            VerificationCodeRepository verificationCodeRepository,
            PasswordEncoderPort passwordEncoderPort,
            EventPublisherPort eventPublisherPort,
            MfaSetupPort mfaSetupPort
    ) {
        return new UserServiceImpl(
                userRepository,
                verificationCodeRepository,
                passwordEncoderPort,
                eventPublisherPort,
                mfaSetupPort
        );
    }
}
