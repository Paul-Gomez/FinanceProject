package com.fincore.identity.application;

import java.time.Duration;

public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(Duration remaining) {
        super("Demasiados intentos fallidos: la cuenta está bloqueada temporalmente");
        this.retryAfterSeconds = Math.max(1, remaining.toSeconds());
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
