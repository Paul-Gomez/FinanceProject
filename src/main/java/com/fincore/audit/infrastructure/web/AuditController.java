package com.fincore.audit.infrastructure.web;

import com.fincore.audit.domain.AuditLog;
import com.fincore.audit.infrastructure.AuditLogRepository;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.shared.audit.AuditAction;
import com.fincore.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@Transactional(readOnly = true)
public class AuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditLogResponse(UUID id, UUID userId, String action, String entityType, UUID entityId,
                                   String ipAddress, String userAgent, Map<String, Object> details, Instant createdAt) {
        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getUserId(), log.getAction(), log.getEntityType(),
                    log.getEntityId(), log.getIpAddress(), log.getUserAgent(), log.getDetails(), log.getCreatedAt());
        }
    }

    /** Historial de acciones del propio usuario. */
    @GetMapping("/api/v1/audit")
    public PageResponse<AuditLogResponse> mine(@CurrentUserId UUID userId,
                                               @RequestParam(required = false) AuditAction action,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = pageable(page, size);
        Page<AuditLog> logs = action == null
                ? repository.findAllByUserId(userId, pageable)
                : repository.findAllByUserIdAndAction(userId, action.name(), pageable);
        return PageResponse.from(logs, AuditLogResponse::from);
    }

    /** Solo administradores (la ruta /api/v1/admin/** exige ROLE_ADMIN). */
    @GetMapping("/api/v1/admin/audit")
    public PageResponse<AuditLogResponse> all(@RequestParam(required = false) UUID userId,
                                              @RequestParam(required = false) AuditAction action,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = pageable(page, size);
        Page<AuditLog> logs;
        if (userId != null) {
            logs = action == null ? repository.findAllByUserId(userId, pageable)
                    : repository.findAllByUserIdAndAction(userId, action.name(), pageable);
        } else {
            logs = action == null ? repository.findAll(pageable) : repository.findAllByAction(action.name(), pageable);
        }
        return PageResponse.from(logs, AuditLogResponse::from);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
