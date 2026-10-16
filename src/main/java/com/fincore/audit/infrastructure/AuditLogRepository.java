package com.fincore.audit.infrastructure;

import com.fincore.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByUserId(UUID userId, Pageable pageable);

    Page<AuditLog> findAllByUserIdAndAction(UUID userId, String action, Pageable pageable);

    Page<AuditLog> findAllByAction(String action, Pageable pageable);
}
