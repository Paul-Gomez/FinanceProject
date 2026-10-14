package com.fincore.identity.application;

import com.fincore.identity.domain.TokenPurpose;
import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cambio y recuperación de contraseña, y verificación de email. */
@Service
public class AccountSecurityService {

    private final AppUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokens;
    private final SessionService sessionService;

    public AccountSecurityService(AppUserJpaRepository userRepository, PasswordEncoder passwordEncoder,
                                  OneTimeTokenService oneTimeTokens, SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.oneTimeTokens = oneTimeTokens;
        this.sessionService = sessionService;
    }

    /** Las demás sesiones se cierran: si alguien conocía la contraseña anterior, pierde el acceso. */
    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongCurrentPasswordException();
        }
        PasswordPolicy.check(newPassword, user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidPasswordException("tiene que ser distinta de la actual");
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        sessionService.revokeAllExcept(userId, currentSessionId, SessionService.REASON_PASSWORD_CHANGED);
    }

    /**
     * Siempre "funciona" desde fuera: exista o no el email, el endpoint responde igual, para no
     * revelar qué emails tienen cuenta. Solo si existe se genera el token y se envía el email.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(AppUser.normalizeEmail(email))
                .filter(AppUser::canLogIn)
                .ifPresent(user -> oneTimeTokens.issue(user, TokenPurpose.PASSWORD_RESET));
    }

    /** Se cierran todas las sesiones (no hay "sesión actual": quien resetea no estaba dentro) y se quita el bloqueo. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        UUID userId = oneTimeTokens.consume(token, TokenPurpose.PASSWORD_RESET);
        AppUser user = userRepository.findById(userId).orElseThrow(InvalidOneTimeTokenException::new);
        PasswordPolicy.check(newPassword, user.getEmail());

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.clearLoginLock();
        sessionService.revokeAll(userId, SessionService.REASON_PASSWORD_CHANGED);
    }

    @Transactional
    public void sendEmailVerification(UUID userId) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        if (!user.isEmailVerified()) {
            oneTimeTokens.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        UUID userId = oneTimeTokens.consume(token, TokenPurpose.EMAIL_VERIFICATION);
        userRepository.findById(userId).orElseThrow(InvalidOneTimeTokenException::new).markEmailVerified();
    }
}
