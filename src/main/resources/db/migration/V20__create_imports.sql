-- Huella de un movimiento importado. Con el índice único, la base de datos garantiza que el mismo
-- movimiento bancario no se importe dos veces en la misma cuenta, aunque lleguen dos importaciones a la vez.
ALTER TABLE transactions ADD COLUMN import_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_transactions_account_import_fingerprint
    ON transactions(account_id, import_fingerprint) WHERE import_fingerprint IS NOT NULL;

CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    file_name VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    imported_rows INT NOT NULL DEFAULT 0,
    duplicate_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    finished_at TIMESTAMP,
    CONSTRAINT chk_import_jobs_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_import_jobs_owner ON import_jobs(owner_id, created_at DESC);

-- Resultado de cada fila del fichero, para el informe descargable.
CREATE TABLE import_rows (
    import_job_id UUID NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
    row_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_date DATE,
    amount NUMERIC(19,4),
    description VARCHAR(255),
    message VARCHAR(500),
    transaction_id UUID,
    PRIMARY KEY (import_job_id, row_number),
    CONSTRAINT chk_import_rows_status CHECK (status IN ('IMPORTED', 'DUPLICATE', 'ERROR'))
);
