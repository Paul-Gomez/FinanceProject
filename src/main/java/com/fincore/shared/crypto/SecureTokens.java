package com.fincore.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (refresh, verificación de email, reset de contraseña). Al cliente se le da el
 * token en claro y en base de datos solo se guarda su SHA-256: si alguien roba la tabla, no puede
 * usar los tokens. No hace falta bcrypt porque son 256 bits aleatorios, no contraseñas adivinables.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
