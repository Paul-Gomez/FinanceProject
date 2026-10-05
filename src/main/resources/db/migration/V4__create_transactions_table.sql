CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    -- Importe con signo: positivo suma al saldo, negativo resta. Así el saldo
    -- se puede reconstruir siempre con un SUM sobre los movimientos.
    amount NUMERIC(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    category_id UUID,
    description VARCHAR(255),
    transaction_date DATE NOT NULL,
    reference VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_transactions_amount_not_zero CHECK (amount <> 0),
    CONSTRAINT chk_transactions_income_positive CHECK (type <> 'INCOME' OR amount > 0),
    CONSTRAINT chk_transactions_expense_negative CHECK (type <> 'EXPENSE' OR amount < 0)
);

CREATE INDEX idx_transactions_account_date ON transactions(account_id, transaction_date DESC, id);
