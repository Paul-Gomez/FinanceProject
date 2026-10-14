package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

/** Mismo mensaje si el email no existe o si la contraseña es incorrecta: no se revela qué falló. */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorType.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email o contraseña incorrectos");
    }
}
