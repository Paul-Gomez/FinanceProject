package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidPasswordException extends BusinessException {

    public InvalidPasswordException(String reason) {
        super(ErrorType.BAD_REQUEST, "INVALID_PASSWORD", "La contraseña no es válida: " + reason);
    }
}
