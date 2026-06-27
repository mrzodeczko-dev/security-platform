package com.rzodeczko.application.event;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId) {
}
