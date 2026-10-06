CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- owner_id NULL = categoría del sistema, visible para todos y de solo lectura.
    owner_id UUID REFERENCES users(id),
    name VARCHAR(60) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#9E9E9E',
    icon VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_categories_kind CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_categories_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

-- Un NULL en owner_id no cuenta como igual a otro NULL en un índice único,
-- así que se sustituye por un UUID fijo para que las del sistema tampoco se repitan.
CREATE UNIQUE INDEX uq_categories_owner_name_kind
    ON categories (COALESCE(owner_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name), kind);

CREATE INDEX idx_categories_owner_id ON categories(owner_id);

INSERT INTO categories (name, kind, color, icon) VALUES
    ('Alimentación', 'EXPENSE', '#4CAF50', 'utensils'),
    ('Transporte', 'EXPENSE', '#2196F3', 'bus'),
    ('Vivienda', 'EXPENSE', '#795548', 'home'),
    ('Ocio', 'EXPENSE', '#E91E63', 'gamepad'),
    ('Salud', 'EXPENSE', '#F44336', 'heart'),
    ('Compras', 'EXPENSE', '#FF9800', 'shopping-bag'),
    ('Suscripciones', 'EXPENSE', '#9C27B0', 'repeat'),
    ('Educación', 'EXPENSE', '#3F51B5', 'book'),
    ('Otros gastos', 'EXPENSE', '#9E9E9E', 'ellipsis'),
    ('Nómina', 'INCOME', '#009688', 'briefcase'),
    ('Ingresos extra', 'INCOME', '#8BC34A', 'plus-circle'),
    ('Otros ingresos', 'INCOME', '#607D8B', 'ellipsis');
