package com.rzodeczko.application.port;

import com.rzodeczko.application.dto.MfaSetupResultDto;


public interface MfaSetupPort {
    MfaSetupResultDto generateCredentials(String username);
}
