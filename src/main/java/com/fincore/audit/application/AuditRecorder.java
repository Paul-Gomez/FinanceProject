package com.fincore.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.shared.audit.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Escribe en audit_logs.
 * - Acciones de negocio: justo antes del commit, en la MISMA transacción y conexión que la
 *   operación. La acción y su registro se confirman o se deshacen juntos (no queda registrado algo
 *   que no pasó, ni pasa algo sin quedar registrado), y no hace falta otra conexión del pool.
 *   Primero se hizo con AFTER_COMMIT + REQUIRES_NEW y el test de concurrencia de idempotencia
 *   agotó el pool: cada petición retenía su conexión y pedía otra para auditar.
 * - Eventos "immediate" (por ejemplo, un login fallido): al momento y en su propia transacción,
 *   porque la transacción de quien los publica termina en rollback. La condición del listener se
 *   evalúa ANTES de entrar en el proxy transaccional: solo estos eventos abren transacción nueva.
 */
@Component
public class AuditRecorder {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditRecorder(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true,
            condition = "!#event.immediate()")
    public void withTheOperation(AuditEvent event) {
        write(event);
    }

    @EventListener(condition = "#event.immediate()")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void immediately(AuditEvent event) {
        write(event);
    }

    private void write(AuditEvent event) {
        try {
            HttpServletRequest request = currentRequest();
            jdbc.update("""
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id, ip_address, user_agent, details, created_at)
                    VALUES (:userId, :action, :entityType, :entityId, :ip, :userAgent, CAST(:details AS jsonb), :createdAt)
                    """, new MapSqlParameterSource()
                    .addValue("userId", event.userId())
                    .addValue("action", event.action().name())
                    .addValue("entityType", event.entityType())
                    .addValue("entityId", event.entityId())
                    .addValue("ip", request != null ? request.getRemoteAddr() : null)
                    .addValue("userAgent", request != null ? truncate(request.getHeader("User-Agent"), 255) : null)
                    .addValue("details", objectMapper.writeValueAsString(event.details()))
                    .addValue("createdAt", LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría", e);
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
