package com.fincore.identity.application;

/** Mismo mensaje si el email no existe o si la contraseña es incorrecta: no se revela qué falló. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email o contraseña incorrectos");
    }
}
