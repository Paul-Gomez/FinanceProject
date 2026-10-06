package com.fincore.categories.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 60) String name,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "debe ser un color hexadecimal tipo #AABBCC") String color,
        @Size(max = 50) String icon
) {
}
