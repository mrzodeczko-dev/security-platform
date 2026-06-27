package com.rzodeczko.domain.repository;

import com.rzodeczko.domain.model.VerificationCode;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository {
    VerificationCode save(VerificationCode code);

    Optional<VerificationCode> findByCode(String code);

    Optional<VerificationCode> findByUserId(UUID userId);

    Optional<VerificationCode> findByUserEmail(String mail);

    void delete(VerificationCode code);
}
