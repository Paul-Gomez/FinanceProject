package com.fincore.identity.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Ya existe una cuenta con ese email");
    }
}
