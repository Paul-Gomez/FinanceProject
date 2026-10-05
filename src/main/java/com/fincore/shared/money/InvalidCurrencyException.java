package com.fincore.shared.money;

public class InvalidCurrencyException extends RuntimeException {

    public InvalidCurrencyException(String code) {
        super("La moneda '%s' no es un código ISO-4217 válido".formatted(code));
    }
}
