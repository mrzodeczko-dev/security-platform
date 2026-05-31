package com.rzodeczko.application.service.impl;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;
import com.rzodeczko.application.event.UserRegisteredEvent;
import com.rzodeczko.application.port.EventPublisherPort;
import com.rzodeczko.application.port.MfaSetupPort;
import com.rzodeczko.application.port.PasswordEncoderPort;
import com.rzodeczko.application.service.UserService;
import com.rzodeczko.domain.exception.*;
import com.rzodeczko.domain.model.User;
import com.rzodeczko.domain.repository.UserRepository;
import com.rzodeczko.domain.repository.VerificationCodeRepository;

public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final EventPublisherPort eventPublisher;
    private final MfaSetupPort mfaSetup;

    public UserServiceImpl(UserRepository userRepository, VerificationCodeRepository verificationCodeRepository, PasswordEncoderPort passwordEncoder, EventPublisherPort eventPublisher, MfaSetupPort mfaSetup) {
        this.userRepository = userRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.mfaSetup = mfaSetup;
    }

    @Override
    public String register(RegisterUserCommand command) {
        if (userRepository.findByUsername(command.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(command.username());
        }

        if (userRepository.findByEmail(command.email()).isPresent()) {
            throw new EmailAlreadyExistsException(command.email());
        }

        if (!command.password().equals(command.passwordConfirmation())) {
            throw new PasswordMismatchException();
        }

        var user = new User(
                command.username(),
                command.email(),
                passwordEncoder.encode(command.password()),
                command.role()
        );

        var savedUser = userRepository.save(user);


        eventPublisher.publishUserRegistered(new UserRegisteredEvent(savedUser.getId()));

        return savedUser.getUsername();
    }


    @Override
    public String activate(String code) {
        var vc = verificationCodeRepository
                .findByCode(code)
                .orElseThrow(VerificationCodeNotFoundException::new);

        if (vc.isExpired()) {
            verificationCodeRepository.delete(vc);
            throw new VerificationCodeExpiredException();
        }

        var user = userRepository
                .findById(vc.getUserId())
                .orElseThrow(() -> new UserNotFoundException(vc.getUserId()));

        if (user.isEnabled()) {
            verificationCodeRepository.delete(vc);
            throw new UserAlreadyActivatedException(user.getUsername());
        }

        user.activate();
        userRepository.save(user);
        verificationCodeRepository.delete(vc);
        return user.getUsername();
    }

    @Override
    public String resendActivationCode(String email) {
        var user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // Usun stary kod jesli istnieje - jeden aktywny kod na user naraz
        verificationCodeRepository
                .findByUserId(user.getId())
                .ifPresent(verificationCodeRepository::delete);

        // Ponownie uzywamy ten sam event co wczesniej
        eventPublisher.publishUserRegistered(new UserRegisteredEvent(user.getId()));
        return user.getUsername();
    }

    @Override
    public String getPasswordResetPermission(String code) {
        var vc = verificationCodeRepository
                .findByCode(code)
                .orElseThrow(VerificationCodeNotFoundException::new);

        if (vc.isExpired()) {
            verificationCodeRepository.delete(vc);
            throw new VerificationCodeExpiredException();
        }

        var user = userRepository
                .findById(vc.getUserId())
                .orElseThrow(() -> new UserNotFoundException(vc.getUserId()));

        if (!user.isEnabled()) {
            // Nie mozna resetowac hasla nieaktywnego konta
            verificationCodeRepository.delete(vc);
            throw new UserNotActivatedException(user.getUsername());
        }

        verificationCodeRepository.delete(vc);
        return user.getEmail();
    }


    @Override
    public String resetPassword(ResetPasswordCommand command) {
        if (!command.password().equals(command.passwordConfirmation())) {
            throw new PasswordMismatchException();
        }

        var user = userRepository
                .findByEmail(command.email())
                .orElseThrow(() -> new UserNotFoundException(command.email()));

        if (!user.isEnabled()) {
            throw new UserNotActivatedException(user.getUsername());
        }

        user.updatePassword(passwordEncoder.encode(command.password()));
        return userRepository
                .save(user)
                .getUsername();
    }

    @Override
    public String setupMfa(String username) {
        var user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.hasMfaActive()) {
            throw new MfaAlreadyActivatedException();
        }

        var mfaResult = mfaSetup.generateCredentials(username);
        user.enableMfa(mfaResult.secret(), mfaResult.qrUrl());
        userRepository.save(user);

        return user.getMfaQrUrl();
    }

    @Override
    public String changeUserRole(ChangeUserRoleCommand command) {
        // Weryfikacja uprawnien requestujacego
        if (!"ROLE_ADMIN".equals(command.requestingUserRole())) {
            throw new InsufficientRoleException("ROLE_ADMIN");
        }

        var user = userRepository
                .findById(command.targetUserId())
                .orElseThrow(() -> new UserNotFoundException(command.targetUserId()));

        user.changeRole(command.newRole());

        return userRepository
                .save(user)
                .getUsername();
    }

    @Override
    public UserCredentialsResultDto verifyCredentials(VerifyCredentialsCommand command) {
        var user = userRepository
                .findByUsername(command.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled()) {
            throw new UserNotActivatedException(user.getUsername());
        }

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new UserCredentialsResultDto(
                user.getId(),
                user.getUsername(),
                user.getRole().getName(),
                user.hasMfaActive()
        );
    }

    @Override
    public MfaDataResultDto getMfaData(GetMfaDataCommand command) {
        var user = userRepository
                .findByUsername(command.username())
                .orElseThrow(() -> new UserNotFoundException(command.username()));

        return new MfaDataResultDto(
                user.getId(),
                user.getUsername(),
                user.getRole().getName(),
                user.getMfaSecret()
        );
    }
}
