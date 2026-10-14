package com.fincore.notifications.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.notifications.application.NotificationService;
import com.fincore.notifications.domain.Notification;
import com.fincore.notifications.domain.NotificationType;
import com.fincore.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record NotificationResponse(UUID id, NotificationType type, String title, String message,
                                       boolean read, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@CurrentUserId UUID userId,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(notificationService.list(userId, unreadOnly, pageable), NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@CurrentUserId UUID userId) {
        return Map.of("unread", notificationService.countUnread(userId));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@CurrentUserId UUID userId, @PathVariable UUID notificationId) {
        notificationService.markRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@CurrentUserId UUID userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
