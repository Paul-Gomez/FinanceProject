package com.fincore.identity.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

public class InvalidRefreshTokenException extends BusinessException {

    public InvalidRefreshTokenException() {
        super(ErrorType.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "El refresh token no es válido, ha caducado o ya se usó; vuelve a iniciar sesión");
    }
}
