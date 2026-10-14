package com.fincore.transactions.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Total gastado (en positivo) en una categoría y moneda dentro de un rango de fechas. */
public record CategorySpending(UUID categoryId, String currencyCode, BigDecimal total) {
}
