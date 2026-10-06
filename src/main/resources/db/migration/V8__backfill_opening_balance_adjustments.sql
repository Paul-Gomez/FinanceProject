-- Hasta ahora el saldo inicial de una cuenta se guardaba directamente en accounts.balance sin
-- ningún movimiento detrás. Para las cuentas que ya existan se crea el ajuste que falta, de modo
-- que balance = SUM(transactions.amount) para todas las cuentas.
INSERT INTO transactions (account_id, type, status, amount, currency_code, description,
                          transaction_date, metadata, created_at)
SELECT a.id,
       'ADJUSTMENT',
       'POSTED',
       a.balance - COALESCE(t.total, 0),
       a.currency_code,
       'Saldo inicial',
       a.created_at::date,
       '{"reason": "OPENING_BALANCE"}'::jsonb,
       a.created_at
FROM accounts a
LEFT JOIN (SELECT account_id, SUM(amount) AS total FROM transactions GROUP BY account_id) t
       ON t.account_id = a.id
WHERE a.balance - COALESCE(t.total, 0) <> 0;
