package com.fincore.accounts.infrastructure.web.dto;

import com.fincore.accounts.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotNull AccountType type,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO-4217 de 3 letras") String currencyCode,
        BigDecimal initialBalance
) {
}
