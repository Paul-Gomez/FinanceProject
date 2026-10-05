package com.fincore.transactions.infrastructure.web.dto;

import com.fincore.transactions.domain.TransactionStatus;
import com.fincore.transactions.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode,
        UUID categoryId,
        String description,
        LocalDate date,
        String reference,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
