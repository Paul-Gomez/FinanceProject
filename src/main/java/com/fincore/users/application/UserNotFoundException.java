package com.fincore.users.application;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("No existe ningún usuario con id " + userId);
    }
}
