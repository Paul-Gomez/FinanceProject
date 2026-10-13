package com.fincore.identity.application;

import com.fincore.identity.domain.UserSession;
import com.fincore.identity.infrastructure.persistence.UserSessionRepository;
import com.fincore.identity.infrastructure.security.JwtProperties;
import com.fincore.identity.infrastructure.security.JwtService;
import com.fincore.shared.crypto.SecureTokens;
import com.fincore.users.domain.AppUser;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Abre una sesión nueva para un usuario y le entrega su par de tokens. */
@Component
public class SessionTokenIssuer {

    private final UserSessionRepository sessionRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public SessionTokenIssuer(UserSessionRepository sessionRepository, JwtService jwtService,
                              JwtProperties jwtProperties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public TokenPair openSession(AppUser user, ClientInfo client) {
        Instant now = clock.instant();
        String refreshToken = SecureTokens.newToken();
        UserSession session = sessionRepository.save(new UserSession(
                user.getId(), SecureTokens.sha256(refreshToken), client.deviceName(), client.userAgent(),
                client.ipAddress(), now, now.plus(jwtProperties.refreshTokenTtl())));

        String accessToken = jwtService.createAccessToken(user.getId(), session.getId(), user.getRole());
        return new TokenPair(accessToken, refreshToken, jwtService.accessTokenTtlSeconds(), session.getId());
    }
}
