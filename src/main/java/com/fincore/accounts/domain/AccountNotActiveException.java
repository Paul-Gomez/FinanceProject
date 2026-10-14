package com.fincore.accounts.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class AccountNotActiveException extends BusinessException {

    public AccountNotActiveException(UUID accountId, AccountStatus status) {
        super(ErrorType.CONFLICT, "ACCOUNT_NOT_ACTIVE", "La cuenta %s está en estado %s y no admite movimientos".formatted(accountId, status));
    }
}
