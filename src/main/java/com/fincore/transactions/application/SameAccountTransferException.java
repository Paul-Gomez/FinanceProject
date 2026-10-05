package com.fincore.transactions.application;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException() {
        super("La cuenta de origen y la de destino no pueden ser la misma");
    }
}
