package com.rzodeczko.infrastructure.event;

import com.rzodeczko.application.event.UserRegisteredEvent;
import com.rzodeczko.application.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishUserRegistered(UserRegisteredEvent event) {
        log.debug("Publishing UserRegisteredEvent for userId={}", event.userId());
        applicationEventPublisher.publishEvent(event);
    }
}
