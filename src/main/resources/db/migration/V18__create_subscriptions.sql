CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    billing_cycle VARCHAR(10) NOT NULL,
    -- Fecha del primer cobro. Los siguientes se calculan desde aquí (inicio + n periodos) y no
    -- sumando un periodo al anterior, para que un cobro del día 31 no derive al 28 tras febrero.
    start_date DATE NOT NULL,
    category_id UUID REFERENCES categories(id),
    account_id UUID REFERENCES accounts(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_subscriptions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_subscriptions_cycle CHECK (billing_cycle IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'))
);

CREATE INDEX idx_subscriptions_owner_active ON subscriptions(owner_id) WHERE active;
