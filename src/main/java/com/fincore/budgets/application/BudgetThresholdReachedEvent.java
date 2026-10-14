package com.fincore.budgets.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Un presupuesto ha llegado al 80 % o al 100 % en el periodo. Lo recogen las notificaciones. */
public record BudgetThresholdReachedEvent(UUID ownerId, UUID budgetId, UUID categoryId, int threshold,
                                          LocalDate periodStart, BigDecimal percentageUsed) {
}
