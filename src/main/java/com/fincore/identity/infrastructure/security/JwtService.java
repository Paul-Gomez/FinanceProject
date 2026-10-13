package com.fincore.identity.infrastructure.security;

import com.fincore.users.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ISSUER = "fincore";
    private static final String SESSION_CLAIM = "sid";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId, UUID sessionId, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(SESSION_CLAIM, sessionId.toString())
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /** Vacío si la firma no es válida, ha caducado o no lo emitimos nosotros. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(SESSION_CLAIM, String.class)),
                    Role.valueOf(claims.get(ROLE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
