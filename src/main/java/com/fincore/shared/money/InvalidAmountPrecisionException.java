package com.fincore.shared.money;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.math.BigDecimal;
import java.util.Currency;

public class InvalidAmountPrecisionException extends BusinessException {

    public InvalidAmountPrecisionException(BigDecimal amount, Currency currency) {
        super(ErrorType.BAD_REQUEST, "INVALID_AMOUNT_PRECISION", "El importe %s tiene más decimales de los que admite %s (%d)".formatted(
                amount.toPlainString(), currency.getCurrencyCode(), currency.getDefaultFractionDigits()));
    }
}
