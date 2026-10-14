package com.fincore.budgets.domain;

import com.fincore.shared.time.DateRange;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum BudgetPeriod {
    MONTHLY,
    YEARLY;

    /** El periodo natural (mes o año) que contiene la fecha. */
    public DateRange rangeContaining(LocalDate date) {
        return switch (this) {
            case MONTHLY -> new DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
            case YEARLY -> new DateRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()));
        };
    }
}
