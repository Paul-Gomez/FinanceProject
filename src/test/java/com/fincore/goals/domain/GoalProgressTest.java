package com.fincore.goals.domain;

import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProgressTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    void carExampleFromTheSpec() {
        GoalProgress progress = GoalProgress.of(Money.of("15000.00", "EUR"), Money.of("6500.00", "EUR"),
                LocalDate.of(2027, 12, 1), GoalStatus.ACTIVE, TODAY);

        assertThat(progress.remaining()).isEqualTo(Money.of("8500.00", "EUR"));
        assertThat(progress.percentage()).isEqualByComparingTo("43.33");
        assertThat(progress.monthsLeft()).isEqualTo(14);
        // 8500 / 14 = 607.142857... se redondea hacia arriba para no quedarse corto
        assertThat(progress.monthlyRecommended()).isEqualTo(Money.of("607.15", "EUR"));
        assertThat(progress.overdue()).isFalse();
    }

    @Test
    void targetThisMonthNeedsEverythingNow() {
        GoalProgress progress = GoalProgress.of(Money.of("1000.00", "EUR"), Money.of("200.00", "EUR"),
                LocalDate.of(2026, 10, 30), GoalStatus.ACTIVE, TODAY);

        assertThat(progress.monthsLeft()).isEqualTo(1);
        assertThat(progress.monthlyRecommended()).isEqualTo(Money.of("800.00", "EUR"));
    }

    @Test
    void pastTargetDateIsOverdue() {
        GoalProgress progress = GoalProgress.of(Money.of("1000.00", "EUR"), Money.of("200.00", "EUR"),
                LocalDate.of(2026, 9, 1), GoalStatus.ACTIVE, TODAY);

        assertThat(progress.overdue()).isTrue();
    }

    @Test
    void noRecommendationWithoutDateOrOnceAchieved() {
        assertThat(GoalProgress.of(Money.of("1000.00", "EUR"), Money.of("200.00", "EUR"), null,
                GoalStatus.ACTIVE, TODAY).monthlyRecommended()).isNull();
        GoalProgress achieved = GoalProgress.of(Money.of("1000.00", "EUR"), Money.of("1200.00", "EUR"),
                LocalDate.of(2027, 1, 1), GoalStatus.ACHIEVED, TODAY);
        assertThat(achieved.monthlyRecommended()).isNull();
        assertThat(achieved.remaining()).isEqualTo(Money.of("0.00", "EUR"));
        assertThat(achieved.percentage()).isEqualByComparingTo("120.00");
    }
}
