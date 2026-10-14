package com.fincore.identity.application;

/** Mismo error si el token no existe, ya se usó o caducó: no se da pista de cuál de las tres. */
public class InvalidOneTimeTokenException extends RuntimeException {

    public InvalidOneTimeTokenException() {
        super("El enlace no es válido o ha caducado");
    }
}
