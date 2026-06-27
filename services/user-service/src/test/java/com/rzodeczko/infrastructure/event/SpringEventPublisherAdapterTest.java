package com.rzodeczko.infrastructure.event;

import com.rzodeczko.application.event.UserRegisteredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpringEventPublisherAdapterTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SpringEventPublisherAdapter adapter;

    @Test
    @DisplayName("should delegate to ApplicationEventPublisher with the same event")
    void shouldDelegateToApplicationEventPublisher() {
        // given
        var event = new UserRegisteredEvent(UUID.randomUUID());

        // when
        adapter.publishUserRegistered(event);

        // then
        verify(applicationEventPublisher).publishEvent(event);
    }
}
