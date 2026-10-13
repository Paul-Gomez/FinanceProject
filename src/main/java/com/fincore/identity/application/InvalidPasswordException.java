package com.fincore.identity.application;

public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String reason) {
        super("La contraseña no es válida: " + reason);
    }
}
