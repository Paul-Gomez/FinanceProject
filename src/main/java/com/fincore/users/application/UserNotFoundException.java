package com.fincore.users.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(UUID userId) {
        super(ErrorType.NOT_FOUND, "USER_NOT_FOUND", "No existe ningún usuario con id " + userId);
    }
}
