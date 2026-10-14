package com.fincore.identity.application;

import com.fincore.identity.infrastructure.persistence.OneTimeTokenRepository;
import com.fincore.identity.infrastructure.persistence.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Limpieza periódica de datos de identidad que ya no sirven. */
@Service
public class IdentityMaintenanceService {

    /** Se guardan un tiempo después de terminar por si hace falta revisar algo (por ejemplo, un robo de token). */
    static final Duration SESSION_RETENTION = Duration.ofDays(30);
    static final Duration TOKEN_RETENTION = Duration.ofDays(7);

    private final UserSessionRepository sessionRepository;
    private final OneTimeTokenRepository tokenRepository;
    private final Clock clock;

    public IdentityMaintenanceService(UserSessionRepository sessionRepository, OneTimeTokenRepository tokenRepository,
                                      Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Transactional
    public int deleteOldSessions() {
        return sessionRepository.deleteFinishedBefore(clock.instant().minus(SESSION_RETENTION));
    }

    @Transactional
    public int deleteOldTokens() {
        Instant cutoff = clock.instant().minus(TOKEN_RETENTION);
        return tokenRepository.deleteFinishedBefore(cutoff);
    }
}
