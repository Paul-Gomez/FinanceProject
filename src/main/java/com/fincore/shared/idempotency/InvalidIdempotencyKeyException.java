package com.fincore.shared.idempotency;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidIdempotencyKeyException extends BusinessException {

    public InvalidIdempotencyKeyException() {
        super(ErrorType.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "La cabecera Idempotency-Key debe tener entre 1 y 100 caracteres");
    }
}
