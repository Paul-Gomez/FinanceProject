package com.fincore.identity.application;

public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("La contraseña actual no es correcta");
    }
}
