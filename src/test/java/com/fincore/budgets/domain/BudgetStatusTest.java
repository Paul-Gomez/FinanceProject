package com.fincore.budgets.domain;

import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetStatusTest {

    @Test
    void underBudget() {
        BudgetStatus status = BudgetStatus.of(Money.of("300.00", "EUR"), Money.of("150.00", "EUR"));

        assertThat(status.remaining()).isEqualTo(Money.of("150.00", "EUR"));
        assertThat(status.percentageUsed()).isEqualByComparingTo("50.00");
        assertThat(status.exceeded()).isFalse();
        assertThat(status.excess()).isEqualTo(Money.of("0.00", "EUR"));
    }

    @Test
    void overBudgetReportsTheExcessAndZeroRemaining() {
        BudgetStatus status = BudgetStatus.of(Money.of("300.00", "EUR"), Money.of("320.50", "EUR"));

        assertThat(status.exceeded()).isTrue();
        assertThat(status.excess()).isEqualTo(Money.of("20.50", "EUR"));
        assertThat(status.remaining()).isEqualTo(Money.of("0.00", "EUR"));
        assertThat(status.percentageUsed()).isEqualByComparingTo("106.83");
    }

    @Test
    void thresholdsUseExactAmountsNotTheRoundedPercentage() {
        // 79.996 % se mostraría redondeado como 80.00 %, pero todavía no ha llegado al 80 %.
        BudgetStatus almost = BudgetStatus.of(Money.of("250.00", "EUR"), Money.of("199.99", "EUR"));
        assertThat(almost.percentageUsed()).isEqualByComparingTo("80.00");
        assertThat(almost.reached(80)).isFalse();

        BudgetStatus exactly = BudgetStatus.of(Money.of("250.00", "EUR"), Money.of("200.00", "EUR"));
        assertThat(exactly.reached(80)).isTrue();
        assertThat(exactly.reached(100)).isFalse();
    }
}
