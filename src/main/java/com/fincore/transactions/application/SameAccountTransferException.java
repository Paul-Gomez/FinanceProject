package com.fincore.transactions.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class SameAccountTransferException extends BusinessException {

    public SameAccountTransferException() {
        super(ErrorType.BAD_REQUEST, "SAME_ACCOUNT_TRANSFER", "La cuenta de origen y la de destino no pueden ser la misma");
    }
}
