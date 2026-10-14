CREATE TABLE savings_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    target_amount NUMERIC(19,4) NOT NULL,
    -- Materializado: siempre igual a la suma de goal_contributions de este objetivo.
    saved_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    target_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    achieved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_goals_target_positive CHECK (target_amount > 0),
    CONSTRAINT chk_goals_saved_not_negative CHECK (saved_amount >= 0),
    CONSTRAINT chk_goals_status CHECK (status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED'))
);

CREATE INDEX idx_savings_goals_owner ON savings_goals(owner_id);

-- Aportaciones (positivas) y retiradas (negativas).
CREATE TABLE goal_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    amount NUMERIC(19,4) NOT NULL,
    contribution_date DATE NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_goal_contributions_not_zero CHECK (amount <> 0)
);

CREATE INDEX idx_goal_contributions_goal ON goal_contributions(goal_id, contribution_date DESC);
