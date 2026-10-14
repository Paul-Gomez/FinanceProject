package com.fincore.accounts.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class AccountNameAlreadyExistsException extends BusinessException {

    public AccountNameAlreadyExistsException(String name) {
        super(ErrorType.CONFLICT, "ACCOUNT_NAME_ALREADY_EXISTS", "Ya tienes una cuenta abierta llamada '%s'".formatted(name));
    }
}
