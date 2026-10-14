package com.fincore.budgets.domain;

import com.fincore.shared.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Situación de un presupuesto en un periodo. El porcentaje se redondea a 2 decimales solo para
 * mostrarlo; los umbrales de alerta se comparan con importes exactos (spent * 100 >= amount * 80)
 * para que un redondeo no haga saltar una alerta antes de tiempo.
 */
public record BudgetStatus(Money budgeted, Money spent, Money remaining, BigDecimal percentageUsed,
                           boolean exceeded, Money excess) {

    public static BudgetStatus of(Money budgeted, Money spent) {
        Money difference = budgeted.subtract(spent);
        boolean exceeded = difference.isNegative();
        Money zero = Money.zero(budgeted.currency());

        BigDecimal percentage = spent.amount()
                .multiply(BigDecimal.valueOf(100))
                .divide(budgeted.amount(), 2, RoundingMode.HALF_UP);

        return new BudgetStatus(budgeted, spent, exceeded ? zero : difference, percentage,
                exceeded, exceeded ? difference.negate() : zero);
    }

    public boolean reached(int thresholdPercent) {
        return spent.amount().multiply(BigDecimal.valueOf(100))
                .compareTo(budgeted.amount().multiply(BigDecimal.valueOf(thresholdPercent))) >= 0;
    }
}
