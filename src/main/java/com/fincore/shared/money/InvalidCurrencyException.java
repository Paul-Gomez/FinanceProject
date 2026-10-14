package com.fincore.shared.money;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidCurrencyException extends BusinessException {

    public InvalidCurrencyException(String code) {
        super(ErrorType.BAD_REQUEST, "INVALID_CURRENCY", "La moneda '%s' no es un código ISO-4217 válido".formatted(code));
    }
}
