package com.fincore.accounts.application;

public class AccountNameAlreadyExistsException extends RuntimeException {

    public AccountNameAlreadyExistsException(String name) {
        super("Ya tienes una cuenta abierta llamada '%s'".formatted(name));
    }
}
