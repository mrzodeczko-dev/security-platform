package com.rzodeczko.infrastructure.persistence.adapter;

import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.domain.repository.VerificationCodeRepository;
import com.rzodeczko.infrastructure.persistence.mapper.VerificationCodeMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaVerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VerificationCodeRepositoryAdapter implements VerificationCodeRepository {

    private final JpaVerificationCodeRepository jpa;
    private final VerificationCodeMapper mapper;

    @Override
    @Transactional
    public VerificationCode save(VerificationCode code) {
        return mapper.toDomain(jpa.save(mapper.toEntity(code)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationCode> findByCode(String code) {
        return jpa.findByCode(code).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationCode> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationCode> findByUserEmail(String mail) {
        return jpa.findByUserEmail(mail).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(VerificationCode code) {
        jpa.deleteById(code.getId());
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpa.deleteByUserId(userId);
    }
}
