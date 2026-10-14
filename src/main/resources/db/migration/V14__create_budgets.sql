CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id),
    amount NUMERIC(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    period VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_budgets_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_budgets_period CHECK (period IN ('MONTHLY', 'YEARLY'))
);

-- Un solo presupuesto activo por categoría, periodo y moneda.
CREATE UNIQUE INDEX uq_budgets_active_category_period
    ON budgets(owner_id, category_id, period, currency_code) WHERE active;

CREATE INDEX idx_budgets_owner_active ON budgets(owner_id) WHERE active;

-- Qué alertas se han enviado ya en cada periodo. La clave única hace que cada umbral salte una
-- sola vez por periodo aunque se registren dos gastos a la vez.
CREATE TABLE budget_alerts (
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    threshold INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (budget_id, period_start, threshold)
);
