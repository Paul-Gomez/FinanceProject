package com.fincore.categories.infrastructure.web.dto;

import com.fincore.categories.domain.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull CategoryKind kind,
        UUID parentId,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "debe ser un color hexadecimal tipo #AABBCC") String color,
        @Size(max = 50) String icon
) {
}
