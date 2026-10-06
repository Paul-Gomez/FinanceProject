package com.fincore.accounts.domain;

import com.fincore.shared.money.Money;

import java.util.UUID;

public class AccountBalanceNotZeroException extends RuntimeException {

    public AccountBalanceNotZeroException(UUID accountId, Money balance) {
        super("No se puede cerrar la cuenta %s porque su saldo es %s; déjala a cero antes de cerrarla"
                .formatted(accountId, balance));
    }
}
