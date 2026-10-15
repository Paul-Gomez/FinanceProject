package com.fincore.users.infrastructure.web.dto;

import com.fincore.users.domain.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Role role, boolean emailVerified,
                           String baseCurrency, Instant createdAt) {
}
