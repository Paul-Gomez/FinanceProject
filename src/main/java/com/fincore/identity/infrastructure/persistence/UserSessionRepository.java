package com.fincore.identity.infrastructure.persistence;

import com.fincore.identity.domain.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("""
            select count(s) > 0 from UserSession s
            where s.id = :sessionId and s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            """)
    boolean isActive(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId, @Param("now") Instant now);

    /** Con bloqueo: dos refresh simultáneos con el mismo token no pueden rotarlo los dos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.refreshTokenHash = :hash")
    Optional<UserSession> findByRefreshTokenHashForUpdate(@Param("hash") String hash);

    Optional<UserSession> findByPreviousRefreshTokenHash(String hash);

    @Query("""
            select s from UserSession s
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Sesiones caducadas o revocadas hace tiempo: ya no sirven para nada y solo ocupan espacio. */
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :cutoff or s.revokedAt < :cutoff")
    int deleteFinishedBefore(@Param("cutoff") Instant cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.userId = :userId and s.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.userId = :userId and s.revokedAt is null and s.id <> :keep
            """)
    int revokeAllForUserExcept(@Param("userId") UUID userId, @Param("keep") UUID keepSessionId,
                               @Param("reason") String reason, @Param("now") Instant now);
}
