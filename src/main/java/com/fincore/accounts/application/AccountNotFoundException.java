package com.fincore.accounts.application;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("No existe ninguna cuenta con id " + accountId);
    }
}
