SET search_path TO finance, public;

DO $$
DECLARE
    required_table text;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'users',
        'subscription_plans',
        'subscriptions',
        'accounts',
        'categories',
        'transactions',
        'budgets',
        'notification_settings',
        'notifications',
        'refresh_tokens'
    ]
    LOOP
        IF to_regclass(format('finance.%I', required_table)) IS NULL THEN
            RAISE EXCEPTION
                'Cannot reconcile db.sql schema: missing finance.% table',
                required_table;
        END IF;
    END LOOP;
END;
$$;

-- Align the legacy account enum and existing account rows.
ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS ck_accounts_type;

UPDATE accounts
SET type = 'SAVINGS'
WHERE type = 'SAVING';

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_type
    CHECK (type IN ('CASH', 'BANK', 'SAVINGS'));

-- Refuse to reinterpret non-monthly data silently.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM budgets
        WHERE period_type <> 'MONTHLY'
           OR rollover_enabled = true
           OR start_date <> date_trunc('month', start_date)::date
           OR end_date <> (
               date_trunc('month', start_date)
               + interval '1 month'
               - interval '1 day'
           )::date
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce monthly MVP budgets: existing incompatible budget rows require manual review';
    END IF;
END;
$$;

ALTER TABLE budgets
    DROP CONSTRAINT IF EXISTS ck_budgets_period_type,
    DROP CONSTRAINT IF EXISTS ck_budgets_date_range,
    DROP CONSTRAINT IF EXISTS ck_budgets_month_range,
    DROP CONSTRAINT IF EXISTS ck_budgets_rollover_disabled;

ALTER TABLE budgets
    ADD CONSTRAINT ck_budgets_period_type
        CHECK (period_type = 'MONTHLY'),
    ADD CONSTRAINT ck_budgets_month_range
        CHECK (
            start_date = date_trunc('month', start_date)::date
            AND end_date = (
                date_trunc('month', start_date)
                + interval '1 month'
                - interval '1 day'
            )::date
        ),
    ADD CONSTRAINT ck_budgets_rollover_disabled
        CHECK (rollover_enabled = false);

-- Keep initial-balance changes cache-safe.
CREATE OR REPLACE FUNCTION accounts_initialize_balance()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = finance, public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.current_balance := NEW.initial_balance;
    ELSIF NEW.initial_balance IS DISTINCT FROM OLD.initial_balance THEN
        NEW.current_balance := OLD.current_balance
            + (NEW.initial_balance - OLD.initial_balance);
    END IF;

    RETURN NEW;
END;
$$;

-- Keep transaction create/update/delete/soft-delete effects cache-safe.
CREATE OR REPLACE FUNCTION maintain_account_balance()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = finance, public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.deleted_at IS NULL THEN
            UPDATE accounts
               SET current_balance = current_balance
                   + transaction_signed_amount(NEW.transaction_type, NEW.amount)
             WHERE id = NEW.account_id;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.deleted_at IS NULL THEN
            UPDATE accounts
               SET current_balance = current_balance
                   - transaction_signed_amount(OLD.transaction_type, OLD.amount)
             WHERE id = OLD.account_id;
        END IF;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL THEN
            UPDATE accounts
               SET current_balance = current_balance
                   - transaction_signed_amount(OLD.transaction_type, OLD.amount)
             WHERE id = OLD.account_id;
        END IF;

        IF NEW.deleted_at IS NULL THEN
            UPDATE accounts
               SET current_balance = current_balance
                   + transaction_signed_amount(NEW.transaction_type, NEW.amount)
             WHERE id = NEW.account_id;
        END IF;

        RETURN NEW;
    END IF;

    RETURN NULL;
END;
$$;

-- Repair any pre-Flyway cache drift from the authoritative ledger.
WITH calculated_balances AS (
    SELECT
        a.id AS account_id,
        a.initial_balance
            + COALESCE(SUM(
                CASE
                    WHEN t.deleted_at IS NULL
                        THEN transaction_signed_amount(t.transaction_type, t.amount)
                    ELSE 0
                END
            ), 0) AS calculated_balance
    FROM accounts a
    LEFT JOIN transactions t ON t.account_id = a.id
    GROUP BY a.id, a.initial_balance
)
UPDATE accounts a
SET current_balance = calculated_balances.calculated_balance
FROM calculated_balances
WHERE a.id = calculated_balances.account_id
  AND a.current_balance IS DISTINCT FROM calculated_balances.calculated_balance;

CREATE OR REPLACE VIEW account_balance_reconciliation AS
SELECT
    a.id AS account_id,
    a.user_id,
    a.name AS account_name,
    a.currency,
    a.initial_balance,
    a.current_balance AS cached_balance,
    a.initial_balance
        + COALESCE(SUM(
            CASE
                WHEN t.deleted_at IS NULL
                    THEN transaction_signed_amount(t.transaction_type, t.amount)
                ELSE 0
            END
        ), 0) AS calculated_balance,
    a.current_balance - (
        a.initial_balance
        + COALESCE(SUM(
            CASE
                WHEN t.deleted_at IS NULL
                    THEN transaction_signed_amount(t.transaction_type, t.amount)
                ELSE 0
            END
        ), 0)
    ) AS balance_difference
FROM accounts a
LEFT JOIN transactions t ON t.account_id = a.id
WHERE a.deleted_at IS NULL
GROUP BY
    a.id,
    a.user_id,
    a.name,
    a.currency,
    a.initial_balance,
    a.current_balance;

CREATE OR REPLACE VIEW budget_progress AS
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

ALTER FUNCTION set_updated_at()
    SET search_path = finance, public;
ALTER FUNCTION transaction_signed_amount(varchar, numeric)
    SET search_path = finance, public;
ALTER FUNCTION accounts_initialize_balance()
    SET search_path = finance, public;
ALTER FUNCTION validate_transaction_ownership()
    SET search_path = finance, public;
ALTER FUNCTION maintain_account_balance()
    SET search_path = finance, public;
ALTER FUNCTION validate_budget_category()
    SET search_path = finance, public;
ALTER FUNCTION create_default_notification_settings()
    SET search_path = finance, public;
ALTER FUNCTION normalize_notification_read_state()
    SET search_path = finance, public;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM account_balance_reconciliation
        WHERE balance_difference <> 0
    ) THEN
        RAISE EXCEPTION
            'Account balance cache reconciliation failed after migration';
    END IF;
END;
$$;
