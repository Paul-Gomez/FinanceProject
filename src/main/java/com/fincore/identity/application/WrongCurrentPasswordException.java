package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class WrongCurrentPasswordException extends BusinessException {

    public WrongCurrentPasswordException() {
        super(ErrorType.BAD_REQUEST, "WRONG_CURRENT_PASSWORD", "La contraseña actual no es correcta");
    }
}
