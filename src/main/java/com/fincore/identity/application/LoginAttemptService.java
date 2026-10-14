package com.fincore.identity.application;

import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Cuenta los intentos fallidos de login y bloquea la cuenta al llegar al máximo.
 * REQUIRES_NEW porque el login fallido termina lanzando una excepción que deshace la transacción
 * del login: si el contador se guardara en esa misma transacción, nunca llegaría a subir.
 */
@Service
public class LoginAttemptService {

    private final AppUserJpaRepository userRepository;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(AppUserJpaRepository userRepository, Clock clock,
                               @Value("${fincore.security.login.max-attempts:5}") int maxAttempts,
                               @Value("${fincore.security.login.lock-duration:15m}") Duration lockDuration) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        userRepository.registerFailedLogin(userId, maxAttempts, clock.instant().plus(lockDuration));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        userRepository.resetFailedLogins(userId);
    }
}
