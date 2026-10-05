package com.fincore.transactions.application;

import com.fincore.transactions.domain.TransactionType;

public class UnsupportedTransactionTypeException extends RuntimeException {

    public UnsupportedTransactionTypeException(TransactionType type) {
        super("El tipo de movimiento %s no se puede crear por este endpoint".formatted(type));
    }
}
