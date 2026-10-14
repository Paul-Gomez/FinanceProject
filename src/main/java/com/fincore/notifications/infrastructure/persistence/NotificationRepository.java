package com.fincore.notifications.infrastructure.persistence;

import com.fincore.notifications.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Devuelve 0 si ya existía una notificación con la misma clave de deduplicación para ese usuario. */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, user_id, type, title, message, dedupe_key, created_at)
            VALUES (gen_random_uuid(), :userId, :type, :title, :message, :dedupeKey, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfNew(@Param("userId") UUID userId, @Param("type") String type, @Param("title") String title,
                    @Param("message") String message, @Param("dedupeKey") String dedupeKey, @Param("now") Instant now);

    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);

    Page<Notification> findAllByUserIdAndReadAtIsNull(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.userId = :userId and n.readAt is null")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
