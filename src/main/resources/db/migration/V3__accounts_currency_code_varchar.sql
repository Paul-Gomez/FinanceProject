-- Hibernate valida las columnas contra VARCHAR; con CHAR(3) la aplicación no arrancaba.
ALTER TABLE accounts ALTER COLUMN currency_code TYPE VARCHAR(3);
