package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.User;
import com.rzodeczko.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserEntity toEntity(User domain) {
        return UserEntity
                .builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .email(domain.getEmail())
                .password(domain.getPassword())
                .role(domain.getRole())
                .enabled(domain.isEnabled())
                .mfaSecret(domain.getMfaSecret())
                .mfaQrUrl(domain.getMfaQrUrl())
                .build();
    }

    public User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getRole(),
                entity.isEnabled(),
                entity.getMfaSecret(),
                entity.getMfaQrUrl()
        );
    }
}
