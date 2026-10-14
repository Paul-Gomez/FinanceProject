package com.fincore.budgets.infrastructure.web.dto;

import com.fincore.budgets.domain.BudgetPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetRequest(
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO-4217 de 3 letras") String currencyCode,
        @NotNull BudgetPeriod period
) {
}
