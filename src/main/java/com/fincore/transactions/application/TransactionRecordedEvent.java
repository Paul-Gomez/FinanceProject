package com.fincore.transactions.application;

import com.fincore.transactions.domain.TransactionType;

import java.time.LocalDate;
import java.util.UUID;

/** Se publica al registrar un ingreso o gasto. Otros módulos (presupuestos, auditoría) reaccionan a él. */
public record TransactionRecordedEvent(UUID transactionId, UUID ownerId, UUID accountId, TransactionType type,
                                       UUID categoryId, LocalDate date) {
}
