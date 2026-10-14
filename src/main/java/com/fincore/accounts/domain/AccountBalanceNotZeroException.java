package com.fincore.accounts.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.Money;

import java.util.UUID;

public class AccountBalanceNotZeroException extends BusinessException {

    public AccountBalanceNotZeroException(UUID accountId, Money balance) {
        super(ErrorType.CONFLICT, "ACCOUNT_BALANCE_NOT_ZERO", "No se puede cerrar la cuenta %s porque su saldo es %s; déjala a cero antes de cerrarla"
                .formatted(accountId, balance));
    }
}
