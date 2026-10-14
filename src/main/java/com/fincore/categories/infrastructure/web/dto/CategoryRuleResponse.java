package com.fincore.categories.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryRuleResponse(UUID id, String pattern, UUID categoryId, int priority, Instant createdAt) {
}
