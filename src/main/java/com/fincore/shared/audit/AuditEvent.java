package com.fincore.shared.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Acción a auditar. Vive en shared para que cualquier módulo pueda publicarla sin depender del
 * módulo de auditoría. details: contexto mínimo, nunca importes, saldos, contraseñas ni tokens.
 * immediate: se registra aunque la transacción de quien lo publica se deshaga (por ejemplo, un
 * login fallido, que termina con rollback). Si es false, solo se registra si la operación se confirma.
 */
public record AuditEvent(UUID userId, AuditAction action, String entityType, UUID entityId,
                         Map<String, Object> details, boolean immediate) {

    public static AuditEvent of(UUID userId, AuditAction action, String entityType, UUID entityId) {
        return new AuditEvent(userId, action, entityType, entityId, Map.of(), false);
    }

    public static AuditEvent of(UUID userId, AuditAction action, String entityType, UUID entityId,
                                Map<String, Object> details) {
        return new AuditEvent(userId, action, entityType, entityId, details, false);
    }

    public static AuditEvent immediate(UUID userId, AuditAction action, Map<String, Object> details) {
        return new AuditEvent(userId, action, "USER", userId, details, true);
    }
}
