package com.rzodeczko.infrastructure.persistence.adapter;

import com.rzodeczko.domain.model.VerificationCode;
import com.rzodeczko.infrastructure.persistence.entity.VerificationCodeEntity;
import com.rzodeczko.infrastructure.persistence.mapper.VerificationCodeMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaVerificationCodeRepository;
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
class VerificationCodeRepositoryAdapterTest {

    @Mock
    private JpaVerificationCodeRepository jpa;

    @Mock
    private VerificationCodeMapper mapper;

    @InjectMocks
    private VerificationCodeRepositoryAdapter adapter;

    private final UUID codeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private VerificationCode createDomainCode() {
        return new VerificationCode(codeId, "123456", System.currentTimeMillis() + 60000, userId);
    }

    private VerificationCodeEntity createCodeEntity() {
        return VerificationCodeEntity.builder()
                .id(codeId)
                .code("123456")
                .expiresAt(System.currentTimeMillis() + 60000)
                .userId(userId)
                .build();
    }

    @Test
    void save_shouldMapToEntitySaveAndMapBackToDomain() {
        VerificationCode domainCode = createDomainCode();
        VerificationCodeEntity entity = createCodeEntity();
        VerificationCode savedDomainCode = createDomainCode();

        when(mapper.toEntity(domainCode)).thenReturn(entity);
        when(jpa.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(savedDomainCode);

        VerificationCode result = adapter.save(domainCode);

        assertThat(result).isEqualTo(savedDomainCode);
        verify(mapper).toEntity(domainCode);
        verify(jpa).save(entity);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByCode_whenFound_shouldReturnMappedDomain() {
        String code = "123456";
        VerificationCodeEntity entity = createCodeEntity();
        VerificationCode domainCode = createDomainCode();

        when(jpa.findByCode(code)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domainCode);

        Optional<VerificationCode> result = adapter.findByCode(code);

        assertThat(result).isPresent().contains(domainCode);
        verify(jpa).findByCode(code);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByCode_whenNotFound_shouldReturnEmpty() {
        String code = "000000";

        when(jpa.findByCode(code)).thenReturn(Optional.empty());

        Optional<VerificationCode> result = adapter.findByCode(code);

        assertThat(result).isEmpty();
        verify(jpa).findByCode(code);
        verifyNoInteractions(mapper);
    }

    @Test
    void findByUserId_whenFound_shouldReturnMappedDomain() {
        VerificationCodeEntity entity = createCodeEntity();
        VerificationCode domainCode = createDomainCode();

        when(jpa.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domainCode);

        Optional<VerificationCode> result = adapter.findByUserId(userId);

        assertThat(result).isPresent().contains(domainCode);
        verify(jpa).findByUserId(userId);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByUserId_whenNotFound_shouldReturnEmpty() {
        UUID unknownUserId = UUID.randomUUID();

        when(jpa.findByUserId(unknownUserId)).thenReturn(Optional.empty());

        Optional<VerificationCode> result = adapter.findByUserId(unknownUserId);

        assertThat(result).isEmpty();
        verify(jpa).findByUserId(unknownUserId);
        verifyNoInteractions(mapper);
    }

    @Test
    void findByUserEmail_whenFound_shouldReturnMappedDomain() {
        String email = "john@example.com";
        VerificationCodeEntity entity = createCodeEntity();
        VerificationCode domainCode = createDomainCode();

        when(jpa.findByUserEmail(email)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domainCode);

        Optional<VerificationCode> result = adapter.findByUserEmail(email);

        assertThat(result).isPresent().contains(domainCode);
        verify(jpa).findByUserEmail(email);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByUserEmail_whenNotFound_shouldReturnEmpty() {
        String email = "unknown@example.com";

        when(jpa.findByUserEmail(email)).thenReturn(Optional.empty());

        Optional<VerificationCode> result = adapter.findByUserEmail(email);

        assertThat(result).isEmpty();
        verify(jpa).findByUserEmail(email);
        verifyNoInteractions(mapper);
    }

    @Test
    void delete_shouldCallDeleteByIdWithCodeId() {
        VerificationCode domainCode = createDomainCode();

        adapter.delete(domainCode);

        verify(jpa).deleteById(codeId);
        verifyNoMoreInteractions(jpa);
        verifyNoInteractions(mapper);
    }
}
