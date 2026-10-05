package com.fincore.shared.idempotency;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("La cabecera Idempotency-Key debe tener entre 1 y 100 caracteres");
    }
}
