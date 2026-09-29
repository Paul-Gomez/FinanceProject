package com.fincore.accounts.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountRequest(
        @NotBlank String name
) {
}
