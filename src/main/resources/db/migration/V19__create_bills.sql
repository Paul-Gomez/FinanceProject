CREATE TABLE bills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(120) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    category_id UUID REFERENCES categories(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP,
    -- El gasto que se creó al pagarla (para poder ir de la factura al movimiento).
    paid_transaction_id UUID REFERENCES transactions(id),
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_bills_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_bills_dates CHECK (due_date >= issue_date),
    CONSTRAINT chk_bills_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT chk_bills_paid_consistency CHECK ((status = 'PAID') = (paid_at IS NOT NULL))
);

CREATE INDEX idx_bills_owner_due ON bills(owner_id, due_date);
-- Para el job diario: solo las pendientes, ordenadas por vencimiento.
CREATE INDEX idx_bills_pending_due ON bills(due_date) WHERE status = 'PENDING';
