package com.rzodeczko.application.service;

import com.rzodeczko.application.command.LoginCommand;
import com.rzodeczko.application.command.RefreshTokenCommand;
import com.rzodeczko.application.command.VerifyMfaCommand;
import com.rzodeczko.application.dto.LoginResultDto;
import com.rzodeczko.application.dto.TokenPairDto;

public interface AuthService {
    LoginResultDto login(LoginCommand command);
    LoginResultDto verifyMfa(VerifyMfaCommand command);
    TokenPairDto refresh(RefreshTokenCommand command);
}
