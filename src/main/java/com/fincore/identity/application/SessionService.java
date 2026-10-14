package com.fincore.identity.application;

import com.fincore.identity.domain.UserSession;
import com.fincore.identity.infrastructure.persistence.UserSessionRepository;
import com.fincore.identity.infrastructure.security.JwtService;
import com.fincore.shared.crypto.SecureTokens;
import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REVOKED_BY_USER = "REVOKED_BY_USER";
    public static final String REASON_REFRESH_REUSED = "REFRESH_TOKEN_REUSED";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private final UserSessionRepository sessionRepository;
    private final AppUserJpaRepository userRepository;
    private final JwtService jwtService;
    private final Clock clock;

    public SessionService(UserSessionRepository sessionRepository, AppUserJpaRepository userRepository,
                          JwtService jwtService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /**
     * Rotación: cada refresh token sirve una sola vez. Si llega uno que ya se rotó, alguien lo está
     * reutilizando (lo más probable, un token robado): se revoca la sesión entera y quien la tenga,
     * sea el usuario o el atacante, tendrá que volver a hacer login. noRollbackFor es necesario
     * porque la revocación tiene que guardarse aunque se responda con error.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        String hash = SecureTokens.sha256(refreshToken);

        Optional<UserSession> current = sessionRepository.findByRefreshTokenHashForUpdate(hash);
        if (current.isEmpty()) {
            sessionRepository.findByPreviousRefreshTokenHash(hash).ifPresent(reused -> {
                reused.revoke(REASON_REFRESH_REUSED, now);
                log.warn("Refresh token reutilizado: se revoca la sesión {} del usuario {}", reused.getId(), reused.getUserId());
            });
            throw new InvalidRefreshTokenException();
        }

        UserSession session = current.get();
        if (!session.isActiveAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        String newRefreshToken = SecureTokens.newToken();
        session.rotate(SecureTokens.sha256(newRefreshToken), now);

        AppUser user = userRepository.findById(session.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        String accessToken = jwtService.createAccessToken(user.getId(), session.getId(), user.getRole());
        return new TokenPair(accessToken, newRefreshToken, jwtService.accessTokenTtlSeconds(), session.getId());
    }

    @Transactional
    public void logout(UUID sessionId, UUID userId) {
        revoke(sessionId, userId, REASON_LOGOUT);
    }

    @Transactional(readOnly = true)
    public List<UserSession> listActive(UUID userId) {
        return sessionRepository.findActiveByUserId(userId, clock.instant());
    }

    @Transactional
    public void revokeOne(UUID sessionId, UUID userId) {
        revoke(sessionId, userId, REASON_REVOKED_BY_USER);
    }

    @Transactional
    public int revokeAllExcept(UUID userId, UUID keepSessionId, String reason) {
        return sessionRepository.revokeAllForUserExcept(userId, keepSessionId, reason, clock.instant());
    }

    @Transactional
    public int revokeAll(UUID userId, String reason) {
        return sessionRepository.revokeAllForUser(userId, reason, clock.instant());
    }

    private void revoke(UUID sessionId, UUID userId, String reason) {
        UserSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        session.revoke(reason, clock.instant());
    }
}
