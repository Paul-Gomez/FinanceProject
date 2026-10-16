-- Registro de acciones sensibles. Solo se inserta: la aplicación no tiene ninguna operación que
-- modifique o borre filas. En producción, además, el usuario de base de datos de la aplicación no
-- debería tener permisos UPDATE/DELETE sobre esta tabla.
-- Sin FK a users a propósito: el registro tiene que sobrevivir aunque se borre el usuario.
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    -- Contexto mínimo y sin datos financieros (ni importes ni saldos).
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_created ON audit_logs(user_id, created_at DESC);
CREATE INDEX idx_audit_logs_action_created ON audit_logs(action, created_at DESC);
