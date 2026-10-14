package com.fincore.accounts.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidAccountStatusTransitionException extends BusinessException {

    public InvalidAccountStatusTransitionException(AccountStatus from, AccountStatus to) {
        super(ErrorType.CONFLICT, "INVALID_STATUS_TRANSITION", "No se puede pasar una cuenta de %s a %s".formatted(from, to));
    }
}
