-- Un movimiento no se borra nunca: se anula (status = VOIDED) y se revierte su efecto en el saldo.
ALTER TABLE transactions ADD COLUMN voided_at TIMESTAMP;

-- Un reembolso apunta al gasto que devuelve.
ALTER TABLE transactions ADD COLUMN refunded_transaction_id UUID REFERENCES transactions(id);

CREATE INDEX idx_transactions_refunded ON transactions(refunded_transaction_id) WHERE refunded_transaction_id IS NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_refund_link CHECK ((type = 'REFUND') = (refunded_transaction_id IS NOT NULL)),
    ADD CONSTRAINT chk_transactions_refund_positive CHECK (type <> 'REFUND' OR amount > 0),
    ADD CONSTRAINT chk_transactions_voided_consistency CHECK ((status = 'VOIDED') = (voided_at IS NOT NULL)),
    ADD CONSTRAINT chk_transactions_status CHECK (status IN ('POSTED', 'VOIDED'));
