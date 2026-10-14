package com.fincore.identity.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, String deviceName, String userAgent, String ipAddress,
                              Instant createdAt, Instant lastUsedAt, Instant expiresAt, boolean current) {
}
