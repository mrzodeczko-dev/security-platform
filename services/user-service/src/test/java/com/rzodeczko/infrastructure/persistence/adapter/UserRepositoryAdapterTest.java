package com.rzodeczko.infrastructure.persistence.adapter;

import com.rzodeczko.domain.model.Email;
import com.rzodeczko.domain.model.Role;
import com.rzodeczko.domain.model.User;
import com.rzodeczko.domain.model.Username;
import com.rzodeczko.infrastructure.persistence.entity.UserEntity;
import com.rzodeczko.infrastructure.persistence.mapper.UserMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock
    private JpaUserRepository jpa;

    @Mock
    private UserMapper mapper;

    @InjectMocks
    private UserRepositoryAdapter adapter;

    private final UUID userId = UUID.randomUUID();

    private User createDomainUser() {
        return new User(userId, new Username("johndoe"), new Email("john@example.com"), "encoded-password",
                Role.USER, true, "mfa-secret", "mfa-qr-url");
    }

    private UserEntity createUserEntity() {
        return UserEntity.builder()
                .id(userId)
                .username("johndoe")
                .email("john@example.com")
                .password("encoded-password")
                .role(Role.USER)
                .enabled(true)
                .mfaSecret("mfa-secret")
                .mfaQrUrl("mfa-qr-url")
                .build();
    }

    @Test
    void save_shouldMapToEntitySaveAndMapBackToDomain() {
        User domainUser = createDomainUser();
        UserEntity entity = createUserEntity();
        User savedDomainUser = createDomainUser();

        when(mapper.toEntity(domainUser)).thenReturn(entity);
        when(jpa.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(savedDomainUser);

        User result = adapter.save(domainUser);

        assertThat(result).isEqualTo(savedDomainUser);
        verify(mapper).toEntity(domainUser);
        verify(jpa).save(entity);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findById_whenFound_shouldReturnMappedDomain() {
        UserEntity entity = createUserEntity();
        User domainUser = createDomainUser();

        when(jpa.findById(userId)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domainUser);

        Optional<User> result = adapter.findById(userId);

        assertThat(result).isPresent().contains(domainUser);
        verify(jpa).findById(userId);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findById_whenNotFound_shouldReturnEmpty() {
        when(jpa.findById(userId)).thenReturn(Optional.empty());

        Optional<User> result = adapter.findById(userId);

        assertThat(result).isEmpty();
        verify(jpa).findById(userId);
        verifyNoInteractions(mapper);
    }

    @Test
    void findByUsername_whenFound_shouldReturnMappedDomain() {
        String username = "johndoe";
        UserEntity entity = createUserEntity();
        User domainUser = createDomainUser();

        when(jpa.findByUsername(username)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domainUser);

        Optional<User> result = adapter.findByUsername(username);

        assertThat(result).isPresent().contains(domainUser);
        verify(jpa).findByUsername(username);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByUsername_whenNotFound_shouldReturnEmpty() {
        String username = "unknown";

        when(jpa.findByUsername(username)).thenReturn(Optional.empty());

        Optional<User> result = adapter.findByUsername(username);

        assertThat(result).isEmpty();
        verify(jpa).findByUsername(username);
        verifyNoInteractions(mapper);
    }

    @Test
    void findByEmail_whenFound_shouldReturnMappedDomain() {
        String email = "john@example.com";
        UserEntity entity = createUserEntity();
        User domainUser = createDomainUser();

        when(jpa.findByEmail(email)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domainUser);

        Optional<User> result = adapter.findByEmail(email);

        assertThat(result).isPresent().contains(domainUser);
        verify(jpa).findByEmail(email);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByEmail_whenNotFound_shouldReturnEmpty() {
        String email = "unknown@example.com";

        when(jpa.findByEmail(email)).thenReturn(Optional.empty());

        Optional<User> result = adapter.findByEmail(email);

        assertThat(result).isEmpty();
        verify(jpa).findByEmail(email);
        verifyNoInteractions(mapper);
    }
}
