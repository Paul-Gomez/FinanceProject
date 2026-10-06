package com.fincore.users.application;

import java.util.UUID;

public class UnknownOwnerException extends RuntimeException {

    public UnknownOwnerException(UUID ownerId) {
        super("No existe ningún usuario con id " + ownerId);
    }
}
