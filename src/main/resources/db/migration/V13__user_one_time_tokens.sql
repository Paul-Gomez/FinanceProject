-- Tokens de un solo uso para verificar el email y recuperar la contraseña. Igual que con el
-- refresh token, solo se guarda el hash: con una copia de esta tabla no se puede usar ninguno.
CREATE TABLE user_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    CONSTRAINT chk_user_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_user_tokens_pending ON user_tokens(user_id, purpose) WHERE used_at IS NULL;
