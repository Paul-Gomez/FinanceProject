package com.fincore.shared.idempotency;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("La Idempotency-Key '%s' ya se usó con una petición distinta".formatted(key));
    }
}
