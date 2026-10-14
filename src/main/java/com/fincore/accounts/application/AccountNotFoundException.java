package com.fincore.accounts.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(UUID accountId) {
        super(ErrorType.NOT_FOUND, "ACCOUNT_NOT_FOUND", "No existe ninguna cuenta con id " + accountId);
    }
}
