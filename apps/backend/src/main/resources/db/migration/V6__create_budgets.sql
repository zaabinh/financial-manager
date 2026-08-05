SET search_path TO finance, public;

CREATE TABLE budgets (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL,
    category_id         uuid NOT NULL,
    name                varchar(120),
    period_type         varchar(20) NOT NULL DEFAULT 'MONTHLY',
    start_date          date NOT NULL,
    end_date            date NOT NULL,
    limit_amount        numeric(19,4) NOT NULL,
    warning_percentage  numeric(5,2) NOT NULL DEFAULT 80.00,
    rollover_enabled    boolean NOT NULL DEFAULT false,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          timestamptz,

    CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_budgets_category
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT ck_budgets_period_type
        CHECK (period_type = 'MONTHLY'),
    CONSTRAINT ck_budgets_month_range
        CHECK (
            start_date = date_trunc('month', start_date)::date
            AND end_date = (
                date_trunc('month', start_date)
                + interval '1 month'
                - interval '1 day'
            )::date
        ),
    CONSTRAINT ck_budgets_rollover_disabled
        CHECK (rollover_enabled = false),
    CONSTRAINT ck_budgets_limit_amount
        CHECK (limit_amount > 0),
    CONSTRAINT ck_budgets_warning_percentage
        CHECK (warning_percentage > 0 AND warning_percentage <= 100)
);

CREATE UNIQUE INDEX uq_budgets_user_category_period
    ON budgets (user_id, category_id, start_date, end_date)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_budgets_user_active_period
    ON budgets (user_id, is_active, start_date, end_date)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_budgets_updated_at
BEFORE UPDATE ON budgets
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION validate_budget_category()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = finance, public
AS $$
DECLARE
    v_category_user_id uuid;
    v_category_type varchar(20);
    v_category_active boolean;
    v_category_deleted_at timestamptz;
BEGIN
    SELECT user_id, transaction_type, is_active, deleted_at
      INTO v_category_user_id, v_category_type, v_category_active, v_category_deleted_at
      FROM categories
     WHERE id = NEW.category_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Category % does not exist', NEW.category_id;
    END IF;

    IF v_category_user_id IS NOT NULL AND v_category_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Budget cannot use another user''s category';
    END IF;

    IF v_category_type <> 'EXPENSE' THEN
        RAISE EXCEPTION 'Budgets can only be assigned to EXPENSE categories';
    END IF;

    IF NOT v_category_active OR v_category_deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'Budget category must be active';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budgets_validate_category
BEFORE INSERT OR UPDATE OF user_id, category_id ON budgets
FOR EACH ROW EXECUTE FUNCTION validate_budget_category();

CREATE VIEW budget_progress AS
SELECT
    b.id AS budget_id,
    b.user_id,
    b.category_id,
    b.name,
    b.start_date,
    b.end_date,
    b.limit_amount,
    b.warning_percentage,
    COALESCE(SUM(t.amount), 0) AS spent_amount,
    b.limit_amount - COALESCE(SUM(t.amount), 0) AS remaining_amount,
    ROUND(
        COALESCE(SUM(t.amount), 0) / NULLIF(b.limit_amount, 0) * 100,
        2
    ) AS usage_percentage,
    CASE
        WHEN COALESCE(SUM(t.amount), 0) >= b.limit_amount THEN 'EXCEEDED'
        WHEN COALESCE(SUM(t.amount), 0)
             >= b.limit_amount * b.warning_percentage / 100 THEN 'WARNING'
        ELSE 'SAFE'
    END AS budget_status
FROM budgets b
LEFT JOIN transactions t
    ON t.user_id = b.user_id
   AND t.category_id = b.category_id
   AND t.transaction_type = 'EXPENSE'
   AND t.deleted_at IS NULL
   AND t.transaction_date BETWEEN b.start_date AND b.end_date
WHERE b.deleted_at IS NULL
  AND b.is_active = true
GROUP BY
    b.id,
    b.user_id,
    b.category_id,
    b.name,
    b.start_date,
    b.end_date,
    b.limit_amount,
    b.warning_percentage;
