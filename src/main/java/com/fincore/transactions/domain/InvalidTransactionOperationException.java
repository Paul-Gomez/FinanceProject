package com.fincore.transactions.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidTransactionOperationException extends BusinessException {

    public InvalidTransactionOperationException(String reason) {
        super(ErrorType.CONFLICT, "INVALID_TRANSACTION_OPERATION", "Operación no válida sobre el movimiento: " + reason);
    }
}
