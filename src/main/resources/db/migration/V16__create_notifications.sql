CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    -- Opcional: identifica el "motivo" del aviso (por ejemplo bill:<id>:due-soon) para que un
    -- job que se ejecuta varias veces no avise dos veces de lo mismo.
    dedupe_key VARCHAR(150),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notifications_user_dedupe ON notifications(user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE read_at IS NULL;
