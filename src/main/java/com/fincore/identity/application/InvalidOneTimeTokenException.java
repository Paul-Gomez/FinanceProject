package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

/** Mismo error si el token no existe, ya se usó o caducó: no se da pista de cuál de las tres. */
public class InvalidOneTimeTokenException extends BusinessException {

    public InvalidOneTimeTokenException() {
        super(ErrorType.BAD_REQUEST, "INVALID_TOKEN", "El enlace no es válido o ha caducado");
    }
}
