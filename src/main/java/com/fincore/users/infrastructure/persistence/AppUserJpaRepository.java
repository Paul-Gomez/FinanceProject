package com.fincore.users.infrastructure.persistence;

import com.fincore.users.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AppUserJpaRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String normalizedEmail);

    /**
     * Incremento atómico en la propia base de datos: con varios intentos a la vez, leer el contador
     * en Java y guardarlo +1 perdería incrementos. Al llegar al máximo se bloquea la cuenta y el
     * contador vuelve a cero, para que al terminar el bloqueo haya otra vez todos los intentos.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE users SET
                locked_until = CASE WHEN failed_login_attempts + 1 >= :maxAttempts THEN :lockUntil ELSE locked_until END,
                failed_login_attempts = CASE WHEN failed_login_attempts + 1 >= :maxAttempts THEN 0 ELSE failed_login_attempts + 1 END
            WHERE id = :userId
            """, nativeQuery = true)
    int registerFailedLogin(@Param("userId") UUID userId, @Param("maxAttempts") int maxAttempts,
                            @Param("lockUntil") Instant lockUntil);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE users SET failed_login_attempts = 0, locked_until = NULL
            WHERE id = :userId AND (failed_login_attempts > 0 OR locked_until IS NOT NULL)
            """, nativeQuery = true)
    int resetFailedLogins(@Param("userId") UUID userId);
}
