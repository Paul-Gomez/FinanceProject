package com.fincore.transactions.application;

import com.fincore.transactions.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que hace falta para registrar un ingreso o un gasto. Es el contrato de la capa de aplicación:
 * lo usan el controller (traduciendo su DTO) y otros módulos, como facturas al pagar una.
 */
public record RecordTransactionCommand(UUID accountId, TransactionType type, BigDecimal amount, String currencyCode,
                                       UUID categoryId, String description, LocalDate date, String reference,
                                       Map<String, Object> metadata) {
}
