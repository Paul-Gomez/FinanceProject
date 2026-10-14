package com.fincore.bills.domain;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidBillDatesException extends BusinessException {

    public InvalidBillDatesException() {
        super(ErrorType.UNPROCESSABLE, "INVALID_BILL_DATES", "La fecha de vencimiento no puede ser anterior a la de emisión");
    }
}
