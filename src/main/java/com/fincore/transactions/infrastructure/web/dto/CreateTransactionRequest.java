package com.fincore.transactions.infrastructure.web.dto;

import com.fincore.transactions.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull UUID accountId,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO-4217 de 3 letras") String currencyCode,
        UUID categoryId,
        @Size(max = 255) String description,
        LocalDate date,
        @Size(max = 100) String reference,
        Map<String, Object> metadata
) {
}
