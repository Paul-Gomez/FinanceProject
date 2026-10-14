package com.fincore.budgets.infrastructure.web.dto;

import com.fincore.budgets.domain.BudgetPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID categoryId,
        BudgetPeriod period,
        String currencyCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal budgeted,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        boolean exceeded,
        BigDecimal excess
) {
}
