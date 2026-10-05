package com.fincore.accounts.domain;

import java.util.UUID;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(UUID accountId, AccountStatus status) {
        super("La cuenta %s está en estado %s y no admite movimientos".formatted(accountId, status));
    }
}
