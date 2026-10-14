package com.fincore.shared.money;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.Currency;

public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super(ErrorType.UNPROCESSABLE, "CURRENCY_MISMATCH", "Currency mismatch: expected %s but got %s".formatted(
                expected.getCurrencyCode(), actual.getCurrencyCode()));
    }
}
