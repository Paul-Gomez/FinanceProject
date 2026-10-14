-- Reglas de categorización automática: "si la descripción contiene <pattern>, la categoría es...".
-- El patrón se guarda ya normalizado (mayúsculas, sin tildes). owner_id NULL = regla del sistema.
CREATE TABLE category_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
    pattern VARCHAR(100) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_category_rules_owner_pattern
    ON category_rules (COALESCE(owner_id, '00000000-0000-0000-0000-000000000000'::uuid), pattern);

CREATE INDEX idx_category_rules_owner ON category_rules(owner_id);

INSERT INTO category_rules (pattern, category_id)
SELECT r.pattern, c.id
FROM (VALUES
          ('MERCADONA', 'Alimentación'), ('CARREFOUR', 'Alimentación'), ('LIDL', 'Alimentación'),
          ('ALDI', 'Alimentación'), ('EROSKI', 'Alimentación'), ('CONSUM', 'Alimentación'),
          ('UBER', 'Transporte'), ('CABIFY', 'Transporte'), ('RENFE', 'Transporte'),
          ('REPSOL', 'Transporte'), ('CEPSA', 'Transporte'), ('METRO', 'Transporte'),
          ('NETFLIX', 'Suscripciones'), ('SPOTIFY', 'Suscripciones'), ('HBO', 'Suscripciones'),
          ('DISNEY', 'Suscripciones'), ('AMAZON PRIME', 'Suscripciones'),
          ('AMAZON', 'Compras'), ('ZARA', 'Compras'), ('IKEA', 'Compras'),
          ('FARMACIA', 'Salud'), ('IBERDROLA', 'Vivienda'), ('ENDESA', 'Vivienda'),
          ('NOMINA', 'Nómina')
     ) AS r(pattern, category_name)
JOIN categories c ON c.name = r.category_name AND c.owner_id IS NULL AND c.parent_id IS NULL;
