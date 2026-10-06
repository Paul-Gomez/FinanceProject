package com.fincore.shared.money;

import java.math.BigDecimal;
import java.util.Currency;

public class InvalidAmountPrecisionException extends RuntimeException {

    public InvalidAmountPrecisionException(BigDecimal amount, Currency currency) {
        super("El importe %s tiene más decimales de los que admite %s (%d)".formatted(
                amount.toPlainString(), currency.getCurrencyCode(), currency.getDefaultFractionDigits()));
    }
}
