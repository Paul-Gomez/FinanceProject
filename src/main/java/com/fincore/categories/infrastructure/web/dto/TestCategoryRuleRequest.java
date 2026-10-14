package com.fincore.categories.infrastructure.web.dto;

import com.fincore.categories.domain.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestCategoryRuleRequest(@NotBlank String description, @NotNull CategoryKind kind) {
}
