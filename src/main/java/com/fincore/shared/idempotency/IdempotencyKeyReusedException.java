package com.fincore.shared.idempotency;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException(String key) {
        super(ErrorType.UNPROCESSABLE, "IDEMPOTENCY_KEY_REUSED", "La Idempotency-Key '%s' ya se usó con una petición distinta".formatted(key));
    }
}
