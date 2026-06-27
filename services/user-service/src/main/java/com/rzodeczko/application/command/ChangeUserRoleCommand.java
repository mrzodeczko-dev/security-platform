package com.rzodeczko.application.command;

import com.rzodeczko.domain.model.Role;

import java.util.UUID;

public record ChangeUserRoleCommand(
        UUID targetUserId,
        Role newRole,
        UUID requestingUserId,
        String requestingUserRole
) {
}
