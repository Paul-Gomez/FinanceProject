package com.fincore.users.application;

import com.fincore.users.domain.AppUser;
import com.fincore.users.domain.Role;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El primer administrador se nombra por configuración (ADMIN_EMAIL) al arrancar: el usuario se
 * registra normal y, si su email coincide, se le da el rol ADMIN. No existe ningún endpoint para
 * hacerse administrador, que sería justo lo primero que probaría un atacante.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserJpaRepository userRepository;
    private final String adminEmail;

    public AdminBootstrap(AppUserJpaRepository userRepository, @Value("${fincore.bootstrap.admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        userRepository.findByEmail(AppUser.normalizeEmail(adminEmail))
                .filter(user -> user.getRole() != Role.ADMIN)
                .ifPresent(user -> {
                    user.promoteToAdmin();
                    log.info("Usuario {} promovido a administrador por configuración", user.getId());
                });
    }
}
