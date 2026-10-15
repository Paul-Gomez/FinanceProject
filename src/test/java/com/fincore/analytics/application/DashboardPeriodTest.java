package com.fincore.analytics.application;

import com.fincore.analytics.application.DashboardService.PeriodType;
import com.fincore.shared.time.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardPeriodTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);

    @Test
    void naturalPeriodsContainingTheDate() {
        assertThat(DashboardService.periodRange(PeriodType.MONTH, DATE, null, null))
                .isEqualTo(new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertThat(DashboardService.periodRange(PeriodType.QUARTER, DATE, null, null))
                .isEqualTo(new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertThat(DashboardService.periodRange(PeriodType.QUARTER, LocalDate.of(2026, 12, 31), null, null))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
        assertThat(DashboardService.periodRange(PeriodType.YEAR, DATE, null, null))
                .isEqualTo(new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void customNeedsBothDates() {
        assertThat(DashboardService.periodRange(PeriodType.CUSTOM, DATE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10)));
        assertThatThrownBy(() -> DashboardService.periodRange(PeriodType.CUSTOM, DATE, null, null))
                .isInstanceOf(AnalyticsService.InvalidDateRangeException.class);
    }
}
