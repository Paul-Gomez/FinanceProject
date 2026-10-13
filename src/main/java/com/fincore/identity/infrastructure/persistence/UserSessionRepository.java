package com.fincore.identity.infrastructure.persistence;

import com.fincore.identity.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("""
            select count(s) > 0 from UserSession s
            where s.id = :sessionId and s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            """)
    boolean isActive(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId, @Param("now") Instant now);
}
