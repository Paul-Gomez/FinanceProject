package com.fincore.goals.domain;

import com.fincore.shared.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Progreso de un objetivo en una fecha.
 * monthsLeft: meses en los que todavía se puede aportar, desde el actual hasta el anterior al de la
 * fecha objetivo (mínimo 1). La aportación recomendada se redondea hacia ARRIBA: si se redondeara
 * hacia abajo, al llegar a la fecha faltarían unos céntimos.
 */
public record GoalProgress(Money saved, Money remaining, BigDecimal percentage, Long monthsLeft,
                           Money monthlyRecommended, boolean overdue) {

    public static GoalProgress of(Money target, Money saved, LocalDate targetDate, GoalStatus status, LocalDate today) {
        Money difference = target.subtract(saved);
        Money remaining = difference.isNegative() ? Money.zero(target.currency()) : difference;
        BigDecimal percentage = saved.amount().multiply(BigDecimal.valueOf(100))
                .divide(target.amount(), 2, RoundingMode.HALF_UP);

        if (targetDate == null || status != GoalStatus.ACTIVE) {
            return new GoalProgress(saved, remaining, percentage, null, null, false);
        }

        boolean overdue = targetDate.isBefore(today);
        long monthsLeft = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)));
        int scale = target.currency().getDefaultFractionDigits();
        BigDecimal monthly = remaining.amount().divide(BigDecimal.valueOf(monthsLeft), scale, RoundingMode.CEILING);

        return new GoalProgress(saved, remaining, percentage, monthsLeft, Money.of(monthly, target.currency()), overdue);
    }
}
