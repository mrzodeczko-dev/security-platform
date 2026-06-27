package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.infrastructure.persistence.entity.VerificationCodeEntity;
import org.springframework.stereotype.Component;

@Component
public class VerificationCodeMapper {
    public VerificationCodeEntity toEntity(VerificationCode domain) {
        return VerificationCodeEntity
                .builder()
                .id(domain.getId())
                .code(domain.getCode())
                .expiresAt(domain.getExpiresAt())
                .userId(domain.getUserId())
                .build();
    }

    public VerificationCode toDomain(VerificationCodeEntity entity) {
        return new VerificationCode(
                entity.getId(),
                entity.getCode(),
                entity.getExpiresAt(),
                entity.getUserId()
        );
    }
}
