package com.fincore.categories.infrastructure.web.dto;

import java.util.UUID;

/** Qué categoría se asignaría a esa descripción; null si ninguna regla encaja. */
public record TestCategoryRuleResponse(UUID categoryId, UUID ruleId, String matchedPattern) {
}
