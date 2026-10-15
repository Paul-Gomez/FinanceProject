package com.fincore.transactions.application;

import com.fincore.transactions.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Un movimiento ya validado que llega de una importación. El importe va con signo. */
public record ImportedTransaction(String fingerprint, TransactionType type, BigDecimal signedAmount,
                                 LocalDate date, String description, String reference, UUID categoryId) {
}
