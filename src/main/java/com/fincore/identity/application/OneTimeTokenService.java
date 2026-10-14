package com.fincore.identity.application;

import com.fincore.identity.domain.OneTimeToken;
import com.fincore.identity.domain.TokenPurpose;
import com.fincore.identity.infrastructure.persistence.OneTimeTokenRepository;
import com.fincore.shared.crypto.SecureTokens;
import com.fincore.users.domain.AppUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OneTimeTokenService {

    static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    private final OneTimeTokenRepository tokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OneTimeTokenService(OneTimeTokenRepository tokenRepository, ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.tokenRepository = tokenRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Crea un token nuevo (invalidando los anteriores del mismo tipo) y pide que se envíe por email. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(AppUser user, TokenPurpose purpose) {
        Instant now = clock.instant();
        Duration ttl = purpose == TokenPurpose.PASSWORD_RESET ? PASSWORD_RESET_TTL : EMAIL_VERIFICATION_TTL;

        tokenRepository.invalidatePending(user.getId(), purpose, now);
        String token = SecureTokens.newToken();
        tokenRepository.save(new OneTimeToken(user.getId(), purpose, SecureTokens.sha256(token), now, now.plus(ttl)));

        eventPublisher.publishEvent(new IdentityEmailRequested(purpose, user.getEmail(), user.getDisplayName(), token));
    }

    /** Gasta el token y devuelve a qué usuario pertenece. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID consume(String rawToken, TokenPurpose purpose) {
        Instant now = clock.instant();
        OneTimeToken token = tokenRepository.findForUpdate(SecureTokens.sha256(rawToken), purpose)
                .filter(t -> t.isUsableAt(now))
                .orElseThrow(InvalidOneTimeTokenException::new);
        token.markUsed(now);
        return token.getUserId();
    }
}
