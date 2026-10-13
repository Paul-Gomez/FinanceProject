package com.fincore.categories.infrastructure.web.dto;

import com.fincore.categories.domain.CategoryKind;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID parentId,
        boolean system,
        String name,
        CategoryKind kind,
        String color,
        String icon,
        Instant createdAt,
        Instant updatedAt
) {
}
