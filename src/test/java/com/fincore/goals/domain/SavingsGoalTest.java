package com.fincore.goals.domain;

import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavingsGoalTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private static SavingsGoal goalOf(String target) {
        return new SavingsGoal(UUID.randomUUID(), "Coche", Money.of(target, "EUR"), null);
    }

    @Test
    void reachingTheTargetMarksItAchievedOnlyOnce() {
        SavingsGoal goal = goalOf("100.00");

        assertThat(goal.contribute(Money.of("60.00", "EUR"), NOW)).isFalse();
        assertThat(goal.contribute(Money.of("40.00", "EUR"), NOW)).isTrue();
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
        assertThat(goal.contribute(Money.of("10.00", "EUR"), NOW)).isFalse();
    }

    @Test
    void withdrawingBelowTheTargetReopensIt() {
        SavingsGoal goal = goalOf("100.00");
        goal.contribute(Money.of("100.00", "EUR"), NOW);

        goal.contribute(Money.of("-30.00", "EUR"), NOW);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goal.getAchievedAt()).isNull();
    }

    @Test
    void cannotWithdrawMoreThanSaved() {
        SavingsGoal goal = goalOf("100.00");
        goal.contribute(Money.of("20.00", "EUR"), NOW);

        assertThatThrownBy(() -> goal.contribute(Money.of("-20.01", "EUR"), NOW))
                .isInstanceOf(InvalidGoalOperationException.class);
        assertThat(goal.saved()).isEqualTo(Money.of("20.00", "EUR"));
    }

    @Test
    void cancelledGoalAcceptsNothingElse() {
        SavingsGoal goal = goalOf("100.00");
        goal.cancel(NOW);

        assertThatThrownBy(() -> goal.contribute(Money.of("10.00", "EUR"), NOW))
                .isInstanceOf(InvalidGoalOperationException.class);
        assertThatThrownBy(() -> goal.cancel(NOW)).isInstanceOf(InvalidGoalOperationException.class);
    }

    @Test
    void loweringTheTargetBelowTheSavedAmountAchievesIt() {
        SavingsGoal goal = goalOf("100.00");
        goal.contribute(Money.of("80.00", "EUR"), NOW);

        assertThat(goal.update("Coche", Money.of("75.00", "EUR"), null, NOW)).isTrue();
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
    }
}
