package com.rzodeczko.infrastructure.persistence.repository;

import com.rzodeczko.infrastructure.persistence.entity.VerificationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaVerificationCodeRepository extends JpaRepository<VerificationCodeEntity, UUID> {
    Optional<VerificationCodeEntity> findByCode(String code);
    Optional<VerificationCodeEntity> findByUserId(UUID userId);

    @Query("select v from VerificationCodeEntity v join UserEntity u on v.userId = u.id where u.email = :email")
    Optional<VerificationCodeEntity> findByUserEmail(@Param("email") String email);

    void deleteByUserId(UUID userId);
}
