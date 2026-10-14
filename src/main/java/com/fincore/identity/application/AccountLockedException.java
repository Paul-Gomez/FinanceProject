package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.time.Duration;
import java.util.Map;

public class AccountLockedException extends BusinessException {

    private final long retryAfterSeconds;

    public AccountLockedException(Duration remaining) {
        super(ErrorType.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED", "Demasiados intentos fallidos: la cuenta está bloqueada temporalmente");
        this.retryAfterSeconds = Math.max(1, remaining.toSeconds());
    }

    @Override
    public Map<String, String> responseHeaders() {
        return Map.of("Retry-After", String.valueOf(retryAfterSeconds));
    }
}
