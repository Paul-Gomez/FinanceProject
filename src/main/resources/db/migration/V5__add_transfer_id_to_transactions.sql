-- Las dos patas de una transferencia comparten transfer_id.
ALTER TABLE transactions ADD COLUMN transfer_id UUID;

CREATE INDEX idx_transactions_transfer_id ON transactions(transfer_id) WHERE transfer_id IS NOT NULL;

-- Un movimiento es TRANSFER si y solo si pertenece a una transferencia.
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_transfer_id
    CHECK ((type = 'TRANSFER') = (transfer_id IS NOT NULL));
