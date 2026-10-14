package com.fincore.identity.application;

import java.util.UUID;

/** También cuando la sesión existe pero es de otro usuario: no se confirma que exista. */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(UUID sessionId) {
        super("No existe la sesión " + sessionId);
    }
}
