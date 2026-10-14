package com.fincore.identity.infrastructure.persistence;

import com.fincore.identity.domain.OneTimeToken;
import com.fincore.identity.domain.TokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    /** Con bloqueo: si llegan dos peticiones con el mismo enlace a la vez, solo una lo puede usar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<OneTimeToken> findForUpdate(@Param("hash") String hash, @Param("purpose") TokenPurpose purpose);

    /** Al pedir un enlace nuevo, los anteriores del mismo tipo dejan de valer. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update OneTimeToken t set t.usedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int invalidatePending(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose,
                          @Param("now") Instant now);
}
