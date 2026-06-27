package com.rzodeczko.infrastructure.persistence.adapter;

import com.rzodeczko.domain.model.User;
import com.rzodeczko.domain.repository.UserRepository;
import com.rzodeczko.infrastructure.persistence.mapper.UserMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpa;
    private final UserMapper mapper;

    @Override
    @Transactional
    public User save(User user) {
        return mapper.toDomain(jpa.save(mapper.toEntity(user)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(mapper::toDomain);
    }
}
