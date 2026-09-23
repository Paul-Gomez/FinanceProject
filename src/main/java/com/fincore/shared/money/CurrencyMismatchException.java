package com.fincore.shared.money;

import java.util.Currency;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected %s but got %s".formatted(
                expected.getCurrencyCode(), actual.getCurrencyCode()));
    }
}
