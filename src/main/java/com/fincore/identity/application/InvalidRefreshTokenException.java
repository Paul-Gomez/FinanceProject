package com.fincore.identity.application;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("El refresh token no es válido, ha caducado o ya se usó; vuelve a iniciar sesión");
    }
}
