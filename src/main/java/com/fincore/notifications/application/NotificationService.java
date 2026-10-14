package com.fincore.notifications.application;

import com.fincore.notifications.domain.Notification;
import com.fincore.notifications.domain.NotificationType;
import com.fincore.notifications.infrastructure.persistence.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** API pública del módulo: el resto de módulos y los jobs crean notificaciones a través de aquí. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** true si se ha creado; false si ya existía una con la misma clave de deduplicación. */
    @Transactional
    public boolean notify(UUID userId, NotificationType type, String title, String message, String dedupeKey) {
        return repository.insertIfNew(userId, type.name(), title, message, dedupeKey, clock.instant()) == 1;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? repository.findAllByUserIdAndReadAtIsNull(userId, pageable)
                : repository.findAllByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        if (repository.markRead(notificationId, userId, clock.instant()) == 0
                && repository.findById(notificationId).filter(n -> n.getUserId().equals(userId)).isEmpty()) {
            throw new NotificationNotFoundException(notificationId);
        }
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, clock.instant());
    }
}
