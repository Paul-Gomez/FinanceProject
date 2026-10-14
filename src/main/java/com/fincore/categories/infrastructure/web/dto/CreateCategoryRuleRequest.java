package com.fincore.categories.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRuleRequest(
        @NotBlank @Size(max = 100) String pattern,
        @NotNull UUID categoryId,
        @Min(0) @Max(1000) int priority
) {
}
