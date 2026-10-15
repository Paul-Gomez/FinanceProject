-- Moneda en la que el usuario quiere ver los totales del dashboard.
ALTER TABLE users ADD COLUMN base_currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

-- Tipos de cambio históricos: 1 unidad de base_currency = rate unidades de quote_currency en rate_date.
-- Se guardan por fecha para poder convertir un movimiento con el tipo de su día, no con el de hoy.
CREATE TABLE exchange_rates (
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate_date DATE NOT NULL,
    rate NUMERIC(19,10) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (base_currency, quote_currency, rate_date),
    CONSTRAINT chk_exchange_rates_positive CHECK (rate > 0),
    CONSTRAINT chk_exchange_rates_distinct CHECK (base_currency <> quote_currency)
);
