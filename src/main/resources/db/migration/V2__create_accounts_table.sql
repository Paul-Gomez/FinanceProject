CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_owner_id ON accounts(owner_id);

-- El nombre solo tiene que ser único entre las cuentas no cerradas del usuario,
-- para poder reutilizarlo si cierra una cuenta antigua y abre otra con el mismo nombre.
CREATE UNIQUE INDEX uq_accounts_owner_name_active ON accounts(owner_id, name) WHERE status <> 'CLOSED';
