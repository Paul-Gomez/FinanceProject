package com.fincore.transactions.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.transactions.domain.TransactionType;

public class UnsupportedTransactionTypeException extends BusinessException {

    public UnsupportedTransactionTypeException(TransactionType type) {
        super(ErrorType.BAD_REQUEST, "UNSUPPORTED_TRANSACTION_TYPE", "El tipo de movimiento %s no se puede crear por este endpoint".formatted(type));
    }
}
