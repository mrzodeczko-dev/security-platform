package com.rzodeczko.infrastructure.service.tx;

import com.rzodeczko.application.command.*;
import com.rzodeczko.application.dto.MfaDataResultDto;
import com.rzodeczko.application.dto.UserCredentialsResultDto;
import com.rzodeczko.application.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("transactionalUserService")
@Slf4j
public class TransactionalUserService implements UserService {

    private final UserService delegate;

    public TransactionalUserService(@Qualifier("userServiceImpl") UserService delegate) {
        this.delegate = delegate;
    }


    @Override
    @Transactional
    public String register(RegisterUserCommand command) {
        return delegate.register(command);
    }

    @Override
    @Transactional
    public String activate(String code) {
        return delegate.activate(code);
    }

    @Override
    @Transactional
    public String resendActivationCode(String email) {
        return delegate.resendActivationCode(email);
    }

    @Override
    @Transactional
    public String getPasswordResetPermission(String code) {
        return delegate.getPasswordResetPermission(code);
    }

    @Override
    @Transactional
    public String resetPassword(ResetPasswordCommand command) {
        return delegate.resetPassword(command);
    }

    @Override
    @Transactional
    public String setupMfa(String username) {
        return delegate.setupMfa(username);
    }

    @Override
    @Transactional
    public String changeUserRole(ChangeUserRoleCommand command) {
        return delegate.changeUserRole(command);
    }

    @Override
    @Transactional(readOnly = true)
    public UserCredentialsResultDto verifyCredentials(VerifyCredentialsCommand command) {
        return delegate.verifyCredentials(command);
    }

    @Override
    @Transactional(readOnly = true)
    public MfaDataResultDto getMfaData(GetMfaDataCommand command) {
        return delegate.getMfaData(command);
    }
}
