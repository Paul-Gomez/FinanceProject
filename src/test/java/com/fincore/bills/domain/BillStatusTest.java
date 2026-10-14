package com.fincore.bills.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BillStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, PAID, true",
            "PENDING, OVERDUE, true",
            "PENDING, CANCELLED, true",
            "OVERDUE, PAID, true",
            "OVERDUE, CANCELLED, true",
            "OVERDUE, PENDING, false",
            "PAID, PENDING, false",
            "PAID, CANCELLED, false",
            "PAID, OVERDUE, false",
            "CANCELLED, PENDING, false",
            "CANCELLED, PAID, false"
    })
    void onlyAllowedTransitions(BillStatus from, BillStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
