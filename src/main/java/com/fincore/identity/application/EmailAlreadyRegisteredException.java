package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class EmailAlreadyRegisteredException extends BusinessException {

    public EmailAlreadyRegisteredException() {
        super(ErrorType.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Ya existe una cuenta con ese email");
    }
}
