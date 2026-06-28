package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Email;
import com.rzodeczko.domain.model.User;
import com.rzodeczko.domain.model.Username;
import com.rzodeczko.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserEntity toEntity(User domain) {
        return UserEntity
                .builder()
                .id(domain.getId())
                .username(domain.getUsername().value())
                .email(domain.getEmail().value())
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
                new Username(entity.getUsername()),
                new Email(entity.getEmail()),
                entity.getPassword(),
                entity.getRole(),
                entity.isEnabled(),
                entity.getMfaSecret(),
                entity.getMfaQrUrl()
        );
    }
}
