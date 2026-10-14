package com.fincore.subscriptions.domain;

import com.fincore.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillingCycleTest {

    @Test
    void monthlyChargeOnThe31stDoesNotDriftAfterFebruary() {
        LocalDate start = LocalDate.of(2026, 1, 31);

        assertThat(BillingCycle.MONTHLY.nextChargeOnOrAfter(start, LocalDate.of(2026, 2, 1)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BillingCycle.MONTHLY.nextChargeOnOrAfter(start, LocalDate.of(2026, 3, 1)))
                .isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(BillingCycle.MONTHLY.nextChargeOnOrAfter(start, LocalDate.of(2026, 4, 1)))
                .isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void chargeOnTheSameDayCountsAsNext() {
        LocalDate start = LocalDate.of(2026, 1, 15);

        assertThat(BillingCycle.MONTHLY.nextChargeOnOrAfter(start, LocalDate.of(2026, 5, 15)))
                .isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(BillingCycle.MONTHLY.nextChargeOnOrAfter(start, LocalDate.of(2025, 12, 1)))
                .isEqualTo(start);
    }

    @Test
    void yearlyFromFebruary29() {
        LocalDate start = LocalDate.of(2028, 2, 29);

        assertThat(BillingCycle.YEARLY.nextChargeOnOrAfter(start, LocalDate.of(2028, 3, 1)))
                .isEqualTo(LocalDate.of(2029, 2, 28));
        assertThat(BillingCycle.YEARLY.nextChargeOnOrAfter(start, LocalDate.of(2031, 3, 1)))
                .isEqualTo(LocalDate.of(2032, 2, 29));
    }

    @Test
    void quarterlyAndWeekly() {
        assertThat(BillingCycle.QUARTERLY.nextChargeOnOrAfter(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 5, 1)))
                .isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(BillingCycle.WEEKLY.nextChargeOnOrAfter(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 14)))
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void weeklySubscriptionHasEveryChargeInTheWindow() {
        Subscription gym = new Subscription(UUID.randomUUID(), "Clase", Money.of("10.00", "EUR"), BillingCycle.WEEKLY,
                LocalDate.of(2026, 10, 5), null, null);

        List<LocalDate> charges = gym.chargesBetween(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 2));

        assertThat(charges).containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 11, 2));
    }

    @Test
    void yearlyAmountPerCycle() {
        assertThat(BillingCycle.WEEKLY.yearlyAmount(new BigDecimal("1.00"))).isEqualByComparingTo("52.00");
        assertThat(BillingCycle.QUARTERLY.yearlyAmount(new BigDecimal("120.00"))).isEqualByComparingTo("480.00");
    }
}
