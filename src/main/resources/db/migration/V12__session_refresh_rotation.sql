-- Al rotar el refresh token se guarda el hash del anterior. Si alguien vuelve a presentar ese
-- token ya usado, es muy probable que esté robado: se revoca la sesión entera.
ALTER TABLE user_sessions ADD COLUMN previous_refresh_token_hash VARCHAR(64);

CREATE INDEX idx_user_sessions_previous_hash ON user_sessions(previous_refresh_token_hash)
    WHERE previous_refresh_token_hash IS NOT NULL;
