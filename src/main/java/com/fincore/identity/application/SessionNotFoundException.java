package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

/** También cuando la sesión existe pero es de otro usuario: no se confirma que exista. */
public class SessionNotFoundException extends BusinessException {

    public SessionNotFoundException(UUID sessionId) {
        super(ErrorType.NOT_FOUND, "SESSION_NOT_FOUND", "No existe la sesión " + sessionId);
    }
}
