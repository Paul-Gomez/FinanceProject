ALTER TABLE users
    ADD COLUMN display_name VARCHAR(100),
    -- NULL solo en usuarios creados antes de tener login: no pueden iniciar sesión.
    ADD COLUMN password_hash VARCHAR(100),
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP,
    ADD COLUMN password_changed_at TIMESTAMP,
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

-- El email se guarda en minúsculas, pero el índice garantiza que tampoco haya duplicados por mayúsculas.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- Una fila por dispositivo/sesión. Solo se guarda el hash del refresh token, nunca el token.
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    device_name VARCHAR(100),
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP NOT NULL DEFAULT now(),
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(30)
);

CREATE INDEX idx_user_sessions_active_by_user ON user_sessions(user_id) WHERE revoked_at IS NULL;
