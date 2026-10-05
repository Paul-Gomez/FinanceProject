CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Sin FK a users: la existencia del usuario la valida la propia operación de negocio.
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    -- SHA-256 del endpoint + cuerpo de la petición, para detectar la misma clave con otro contenido.
    request_hash VARCHAR(64) NOT NULL,
    response_status INT,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);
