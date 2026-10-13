package com.fincore.identity.application;

import java.nio.charset.StandardCharsets;

/**
 * Longitud mínima en vez de reglas de "una mayúscula y un símbolo" (recomendación actual del NIST).
 * El máximo de 72 bytes no es arbitrario: bcrypt ignora todo lo que va después, así que dos
 * contraseñas que solo se diferencien a partir del byte 73 serían la misma.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new InvalidPasswordException("debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidPasswordException("no puede ocupar más de " + MAX_BYTES + " bytes");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw new InvalidPasswordException("no puede ser igual al email");
        }
    }
}
