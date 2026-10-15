package com.fincore.transactions.application;

import com.fincore.transactions.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros del listado de movimientos. Todos opcionales. minAmount/maxAmount se comparan con el
 * importe en valor absoluto (un gasto de 50 está "entre 40 y 60" aunque se guarde como -50).
 */
public record TransactionSearchCriteria(UUID accountId, LocalDate from, LocalDate to, UUID categoryId,
                                        boolean includeSubcategories, TransactionType type, BigDecimal minAmount,
                                        BigDecimal maxAmount, String text, boolean includeVoided) {
}
