package com.fincore.budgets.domain;

import com.fincore.shared.time.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetPeriodTest {

    @Test
    void monthlyPeriodCoversTheWholeCalendarMonth() {
        assertThat(BudgetPeriod.MONTHLY.rangeContaining(LocalDate.of(2028, 2, 15)))
                .isEqualTo(new DateRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
        assertThat(BudgetPeriod.MONTHLY.rangeContaining(LocalDate.of(2026, 12, 31)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void yearlyPeriodCoversTheWholeYear() {
        assertThat(BudgetPeriod.YEARLY.rangeContaining(LocalDate.of(2026, 10, 14)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }
}
