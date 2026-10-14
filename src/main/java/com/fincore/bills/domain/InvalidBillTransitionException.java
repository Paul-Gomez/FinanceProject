package com.fincore.bills.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidBillTransitionException extends BusinessException {

    public InvalidBillTransitionException(BillStatus from, BillStatus to) {
        super(ErrorType.CONFLICT, "INVALID_BILL_TRANSITION",
                "Una factura en estado %s no puede pasar a %s".formatted(from, to));
    }

    public InvalidBillTransitionException(BillStatus current, String action) {
        super(ErrorType.CONFLICT, "INVALID_BILL_TRANSITION",
                "Una factura en estado %s no se puede %s".formatted(current, action));
    }
}
