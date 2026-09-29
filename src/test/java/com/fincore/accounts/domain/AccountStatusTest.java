package com.fincore.accounts.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusTest {

    @ParameterizedTest
    @CsvSource({
            "ACTIVE, ARCHIVED, true",
            "ACTIVE, CLOSED, true",
            "ARCHIVED, ACTIVE, true",
            "ARCHIVED, CLOSED, true",
            "CLOSED, ACTIVE, false",
            "CLOSED, ARCHIVED, false",
            "ACTIVE, ACTIVE, false"
    })
    void validatesAllowedTransitions(AccountStatus from, AccountStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }
}
