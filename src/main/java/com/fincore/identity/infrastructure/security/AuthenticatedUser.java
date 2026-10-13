package com.fincore.identity.infrastructure.security;

import com.fincore.users.domain.Role;

import java.util.UUID;

/** Lo que sabemos del usuario de la petición actual, sacado del access token. */
public final class AuthenticatedUser {

    private final UUID userId;
    private final UUID sessionId;
    private final Role role;

    public AuthenticatedUser(UUID userId, UUID sessionId, Role role) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Role getRole() {
        return role;
    }
}
