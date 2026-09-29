package com.fincore.accounts.infrastructure.web.dto;

import com.fincore.accounts.domain.AccountStatus;
import com.fincore.accounts.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID ownerId,
        String name,
        AccountType type,
        String currencyCode,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
