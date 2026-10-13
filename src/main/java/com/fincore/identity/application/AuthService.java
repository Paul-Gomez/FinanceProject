package com.fincore.identity.application;

import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final AppUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenIssuer tokenIssuer;
    private final String dummyHash;

    public AuthService(AppUserJpaRepository userRepository, PasswordEncoder passwordEncoder,
                       SessionTokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.dummyHash = passwordEncoder.encode("contraseña-de-relleno-para-igualar-tiempos");
    }

    @Transactional
    public AppUser register(String email, String password, String displayName) {
        String normalizedEmail = AppUser.normalizeEmail(email);
        PasswordPolicy.check(password, normalizedEmail);

        try {
            return userRepository.saveAndFlush(
                    new AppUser(normalizedEmail, displayName, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional
    public TokenPair login(String email, String password, ClientInfo client) {
        Optional<AppUser> user = userRepository.findByEmail(AppUser.normalizeEmail(email))
                .filter(AppUser::canLogIn);

        if (user.isEmpty()) {
            // Se compara igualmente contra un hash de relleno para que tarde lo mismo que con un
            // email que sí existe: si no, midiendo el tiempo se podría saber qué emails están registrados.
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.get().getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.openSession(user.get(), client);
    }
}
