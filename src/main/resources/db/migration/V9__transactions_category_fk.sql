-- Hasta ahora transactions.category_id aceptaba cualquier UUID. Los que no apunten a una
-- categoría existente se vacían antes de poner la clave foránea, o la migración fallaría.
UPDATE transactions
SET category_id = NULL
WHERE category_id IS NOT NULL
  AND category_id NOT IN (SELECT id FROM categories);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_category
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT;

-- Necesario para la clave foránea (borrar una categoría) y para los informes por categoría.
CREATE INDEX idx_transactions_category_id ON transactions(category_id) WHERE category_id IS NOT NULL;
