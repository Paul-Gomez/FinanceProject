package com.fincore.accounts.domain;

import java.util.Set;

public enum AccountStatus {
    ACTIVE,
    ARCHIVED,
    CLOSED;

    private static final Set<AccountStatus> ACTIVE_TARGETS = Set.of(ARCHIVED, CLOSED);
    private static final Set<AccountStatus> ARCHIVED_TARGETS = Set.of(ACTIVE, CLOSED);

    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case ACTIVE -> ACTIVE_TARGETS.contains(target);
            case ARCHIVED -> ARCHIVED_TARGETS.contains(target);
            case CLOSED -> false;
        };
    }
}
