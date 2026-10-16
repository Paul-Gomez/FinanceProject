package com.fincore.identity.application;

import com.fincore.identity.domain.TokenPurpose;
import com.fincore.shared.audit.AuditAction;
import com.fincore.shared.audit.AuditEvent;
import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final AppUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenIssuer tokenIssuer;
    private final LoginAttemptService loginAttempts;
    private final OneTimeTokenService oneTimeTokens;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AppUserJpaRepository userRepository, PasswordEncoder passwordEncoder,
                       SessionTokenIssuer tokenIssuer, LoginAttemptService loginAttempts,
                       OneTimeTokenService oneTimeTokens, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.loginAttempts = loginAttempts;
        this.oneTimeTokens = oneTimeTokens;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("contraseña-de-relleno-para-igualar-tiempos");
    }

    @Transactional
    public AppUser register(String email, String password, String displayName) {
        String normalizedEmail = AppUser.normalizeEmail(email);
        PasswordPolicy.check(password, normalizedEmail);

        AppUser user;
        try {
            user = userRepository.saveAndFlush(
                    new AppUser(normalizedEmail, displayName, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
        oneTimeTokens.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        eventPublisher.publishEvent(AuditEvent.of(user.getId(), AuditAction.REGISTER, "USER", user.getId()));
        return user;
    }

    @Transactional
    public TokenPair login(String email, String password, ClientInfo client) {
        Optional<AppUser> user = userRepository.findByEmail(AppUser.normalizeEmail(email))
                .filter(AppUser::canLogIn);

        if (user.isEmpty()) {
            // Se compara igualmente contra un hash de relleno para que tarde lo mismo que con un
            // email que sí existe: si no, midiendo el tiempo se podría saber qué emails están registrados.
            passwordEncoder.matches(password, dummyHash);
            failed(null, "UNKNOWN_EMAIL");
            throw new InvalidCredentialsException();
        }
        AppUser found = user.get();
        Instant now = clock.instant();
        // El bloqueo se mira antes que la contraseña: mientras dure, ni siquiera se comprueba,
        // así que seguir probando contraseñas no sirve de nada.
        if (found.isLockedAt(now)) {
            failed(found, "ACCOUNT_LOCKED");
            throw new AccountLockedException(Duration.between(now, found.getLockedUntil()));
        }
        if (!passwordEncoder.matches(password, found.getPasswordHash())) {
            loginAttempts.recordFailure(found.getId());
            failed(found, "WRONG_PASSWORD");
            throw new InvalidCredentialsException();
        }
        if (found.getFailedLoginAttempts() > 0) {
            loginAttempts.recordSuccess(found.getId());
        }
        TokenPair tokens = tokenIssuer.openSession(found, client);
        eventPublisher.publishEvent(AuditEvent.of(found.getId(), AuditAction.LOGIN, "SESSION", tokens.sessionId(),
                client.deviceName() != null ? Map.of("device", client.deviceName()) : Map.of()));
        return tokens;
    }

    /** Se registra al momento: el login fallido termina en rollback y no llegaría al commit. */
    private void failed(AppUser user, String reason) {
        eventPublisher.publishEvent(AuditEvent.immediate(user != null ? user.getId() : null, AuditAction.LOGIN_FAILED,
                Map.of("reason", reason)));
    }
}
