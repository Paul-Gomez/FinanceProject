package com.fincore.accounts.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class AccountAccessDeniedException extends BusinessException {

    public AccountAccessDeniedException(UUID accountId) {
        super(ErrorType.FORBIDDEN, "ACCESS_DENIED", "No tienes permiso para acceder a la cuenta " + accountId);
    }
}
