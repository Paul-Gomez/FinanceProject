package com.fincore.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * El secreto es obligatorio y tiene que tener al menos 256 bits (lo que exige HS256):
 * si falta o es corto la aplicación no arranca, en vez de arrancar con un secreto débil.
 */
@ConfigurationProperties("fincore.security.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "fincore.security.jwt.secret (JWT_SECRET) es obligatorio y debe tener al menos 32 bytes");
        }
        accessTokenTtl = accessTokenTtl != null ? accessTokenTtl : Duration.ofMinutes(15);
        refreshTokenTtl = refreshTokenTtl != null ? refreshTokenTtl : Duration.ofDays(30);
    }
}
