ALTER TABLE categories
    ADD COLUMN parent_id UUID REFERENCES categories(id) ON DELETE RESTRICT;

CREATE INDEX idx_categories_parent_id ON categories(parent_id) WHERE parent_id IS NOT NULL;

-- Con subcategorías el nombre tiene que ser único dentro del mismo padre, no en todo el usuario:
-- "Otros" puede existir a la vez bajo "Ocio" y bajo "Compras".
DROP INDEX uq_categories_owner_name_kind;
CREATE UNIQUE INDEX uq_categories_owner_parent_name_kind
    ON categories (COALESCE(owner_id, '00000000-0000-0000-0000-000000000000'::uuid),
                   COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid),
                   lower(name), kind);
