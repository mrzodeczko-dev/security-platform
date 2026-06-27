package com.rzodeczko.application.port;

import com.rzodeczko.application.event.UserRegisteredEvent;

public interface EventPublisherPort {
    void publishUserRegistered(UserRegisteredEvent event);
}
