package com.rzodeczko.infrastructure.event;

import com.rzodeczko.application.event.UserRegisteredEvent;
import com.rzodeczko.application.port.EmailPort;
import com.rzodeczko.domain.exception.UserNotFoundException;
import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.domain.repository.UserRepository;
import com.rzodeczko.domain.repository.VerificationCodeRepository;
import com.rzodeczko.infrastructure.configuration.properties.UserActivationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;

// Listener responding to UserRegisteredEvent — generates code and sends activation email.

// @Async — executes in a separate virtual thread.
// HTTP response returns to client IMMEDIATELY after registration commit.

// @TransactionalEventListener(AFTER_COMMIT) — called ONLY after commit
// of registration transaction. If transaction rollback → listener will not execute.

// @Transactional(REQUIRES_NEW) — listener opens its OWN new transaction.
// Needed because AFTER_COMMIT is called when original transaction is closed.
// Without REQUIRES_NEW: save() code → "no transaction active" → TransactionRequiredException.
// If SMTP throws exception → new transaction rollback, but registration already committed.
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationEventListener {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailPort emailPort;
    private final UserActivationProperties properties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.debug("Processing UserRegisteredEvent for userId={}", event.userId());
        var user = userRepository
                .findById(event.userId())
                .orElseThrow(() -> new UserNotFoundException(event.userId()));

        var code = generateCode(properties.codeDigits());
        var expiresAt = System.currentTimeMillis() + properties.expirationMs();

        verificationCodeRepository.save(new VerificationCode(code, expiresAt, user.getId()));

        emailPort.send(
                user.getEmail().value(),
                "Activation Code",
                "Your activation code: %s\nValid for: %d seconds".formatted(
                        code,
                        properties.expirationMs() / 1000
                )
        );

        log.info("Activation mail sent to userId={}", event.userId());
    }

    private String generateCode(int digits) {
        int min = (int) Math.pow(10, digits - 1);
        int max = (int) Math.pow(10, digits) - 1;
        return String.valueOf(SECURE_RANDOM.nextInt(min, max + 1));
    }
}
