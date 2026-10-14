package com.fincore.subscriptions.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum BillingCycle {
    WEEKLY(52),
    MONTHLY(12),
    QUARTERLY(4),
    YEARLY(1);

    private final int chargesPerYear;

    BillingCycle(int chargesPerYear) {
        this.chargesPerYear = chargesPerYear;
    }

    /** Fecha del cobro número n (0 = el primero), siempre calculada desde la fecha de inicio. */
    public LocalDate chargeNumber(LocalDate start, long n) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
            case QUARTERLY -> start.plusMonths(3 * n);
            case YEARLY -> start.plusYears(n);
        };
    }

    /** Primer cobro en la fecha indicada o después. */
    public LocalDate nextChargeOnOrAfter(LocalDate start, LocalDate date) {
        if (!date.isAfter(start)) {
            return start;
        }
        long n = switch (this) {
            case WEEKLY -> ChronoUnit.WEEKS.between(start, date);
            case MONTHLY -> ChronoUnit.MONTHS.between(start, date);
            case QUARTERLY -> ChronoUnit.MONTHS.between(start, date) / 3;
            case YEARLY -> ChronoUnit.YEARS.between(start, date);
        };
        LocalDate candidate = chargeNumber(start, n);
        return candidate.isBefore(date) ? chargeNumber(start, n + 1) : candidate;
    }

    /** Cuánto supone al año un cargo de este importe (52 semanas, 12 meses, 4 trimestres...). */
    public BigDecimal yearlyAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(chargesPerYear));
    }
}
