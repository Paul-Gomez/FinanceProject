package com.fincore.accounts.application;

import java.util.UUID;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(UUID accountId) {
        super("No tienes permiso para acceder a la cuenta " + accountId);
    }
}
