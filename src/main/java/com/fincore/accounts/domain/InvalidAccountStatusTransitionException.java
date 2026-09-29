package com.fincore.accounts.domain;

public class InvalidAccountStatusTransitionException extends RuntimeException {

    public InvalidAccountStatusTransitionException(AccountStatus from, AccountStatus to) {
        super("No se puede pasar una cuenta de %s a %s".formatted(from, to));
    }
}
