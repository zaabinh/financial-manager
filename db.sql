BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance, public;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
	NEW.updated_at := CURRENT_TIMESTAMP;
	RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION transaction_signed_amount(
    p_transaction_type varchar,
    p_amount numeric
)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT CASE
        WHEN p_transaction_type = 'INCOME'  THEN p_amount
        WHEN p_transaction_type = 'EXPENSE' THEN -p_amount
        ELSE 0
    END;
$$;

-- =========================================================
-- USERS
-- =========================================================

CREATE TABLE users (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username        citext NOT NULL,
    email           citext,
    password_hash   varchar(255) NOT NULL,
    display_name    varchar(150) NOT NULL,
    role            varchar(20) NOT NULL DEFAULT 'USER',
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    currency        char(3) NOT NULL DEFAULT 'VND',
    timezone        varchar(100) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      timestamptz,

    CONSTRAINT ck_users_username_not_blank
        CHECK (btrim(username::text) <> ''),
    CONSTRAINT ck_users_email_not_blank
        CHECK (email IS NULL OR btrim(email::text) <> ''),
    CONSTRAINT ck_users_role
        CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'BANNED', 'DELETED')),
    CONSTRAINT ck_users_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_users_deleted_state
        CHECK (
            (status = 'DELETED' AND deleted_at IS NOT NULL)
            OR
            (status <> 'DELETED')
        )
);

CREATE UNIQUE INDEX uq_users_username_active
    ON users (username)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_users_email_active
    ON users (email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_users_status ON users (status);

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- SUBSCRIPTION PLANS AND SUBSCRIPTIONS
-- A user may have subscription history. The current subscription is the
-- active/trialing row, so users.subscription_id is intentionally omitted.
-- =========================================================

CREATE TABLE subscription_plans (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(50) NOT NULL UNIQUE,
    name                varchar(100) NOT NULL,
    price                numeric(19,4) NOT NULL DEFAULT 0,
    currency             char(3) NOT NULL DEFAULT 'VND',
    billing_period       varchar(20) NOT NULL DEFAULT 'MONTHLY',
    max_accounts         integer,
    max_budgets          integer,
    features             jsonb NOT NULL DEFAULT '{}'::jsonb,
    is_active            boolean NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_subscription_plans_code
        CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_subscription_plans_price
        CHECK (price >= 0),
    CONSTRAINT ck_subscription_plans_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_subscription_plans_billing_period
        CHECK (billing_period IN ('FREE', 'MONTHLY', 'YEARLY', 'LIFETIME')),
    CONSTRAINT ck_subscription_plans_max_accounts
        CHECK (max_accounts IS NULL OR max_accounts > 0),
    CONSTRAINT ck_subscription_plans_max_budgets
        CHECK (max_budgets IS NULL OR max_budgets > 0)
);

CREATE TRIGGER trg_subscription_plans_updated_at
BEFORE UPDATE ON subscription_plans
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE subscriptions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid NOT NULL,
    plan_id                 uuid NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'ACTIVE',
    start_at                timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at               timestamptz,
    cancel_at_period_end    boolean NOT NULL DEFAULT false,
    cancelled_at            timestamptz,
    external_provider       varchar(50),
    external_subscription_id varchar(255),
    created_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE RESTRICT,
    CONSTRAINT ck_subscriptions_status
        CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_subscriptions_period
        CHECK (expire_at IS NULL OR expire_at > start_at),
    CONSTRAINT ck_subscriptions_cancelled_at
        CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_subscriptions_one_current_per_user
    ON subscriptions (user_id)
    WHERE status IN ('TRIALING', 'ACTIVE', 'PAST_DUE');

CREATE UNIQUE INDEX uq_subscriptions_external_id
    ON subscriptions (external_provider, external_subscription_id)
    WHERE external_provider IS NOT NULL
      AND external_subscription_id IS NOT NULL;

CREATE INDEX ix_subscriptions_user_status
    ON subscriptions (user_id, status);

CREATE INDEX ix_subscriptions_expire_at
    ON subscriptions (expire_at)
    WHERE expire_at IS NOT NULL;

CREATE TRIGGER trg_subscriptions_updated_at
BEFORE UPDATE ON subscriptions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed a default free plan.
INSERT INTO subscription_plans
    (code, name, price, currency, billing_period, max_accounts, max_budgets, features)
VALUES
    ('FREE', 'Free', 0, 'VND', 'FREE', 5, 10,
     '{"reports": true, "export": false, "multi_currency": false}'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- =========================================================
-- ACCOUNTS
-- current_balance is a database-maintained cache for fast reads.
-- The transaction ledger remains authoritative and the reconciliation view
-- below must report a zero balance_difference during normal operation.
-- =========================================================

CREATE TABLE accounts (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL,
    name                varchar(120) NOT NULL,
    type                varchar(20) NOT NULL,
    initial_balance     numeric(19,4) NOT NULL DEFAULT 0,
    current_balance     numeric(19,4) NOT NULL DEFAULT 0,
    currency            char(3) NOT NULL DEFAULT 'VND',
    is_active           boolean NOT NULL DEFAULT true,
    include_in_total    boolean NOT NULL DEFAULT true,
    display_order       integer NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          timestamptz,

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_accounts_name_not_blank
        CHECK (btrim(name) <> ''),
    CONSTRAINT ck_accounts_type
        CHECK (type IN ('CASH', 'BANK', 'SAVINGS')),
    CONSTRAINT ck_accounts_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_accounts_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_accounts_user_name_active
    ON accounts (user_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_accounts_user_active
    ON accounts (user_id, is_active)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_accounts_updated_at
BEFORE UPDATE ON accounts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION accounts_initialize_balance()
RETURNS trigger
LANGUAGE plpgsql
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

CREATE TRIGGER trg_accounts_initialize_balance
BEFORE INSERT OR UPDATE OF initial_balance ON accounts
FOR EACH ROW EXECUTE FUNCTION accounts_initialize_balance();

-- =========================================================
-- CATEGORIES
-- System/default categories have user_id IS NULL and is_default = true.
-- User-created categories have user_id set and is_default = false.
-- =========================================================

CREATE TABLE categories (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid,
    name                varchar(100) NOT NULL,
    transaction_type    varchar(20) NOT NULL,
    icon                varchar(100),
    color               varchar(20),
    is_default          boolean NOT NULL DEFAULT false,
    is_active           boolean NOT NULL DEFAULT true,
    display_order       integer NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          timestamptz,

    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_categories_name_not_blank
        CHECK (btrim(name) <> ''),
    CONSTRAINT ck_categories_transaction_type
        CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_categories_default_owner
        CHECK (
            (is_default = true AND user_id IS NULL)
            OR
            (is_default = false AND user_id IS NOT NULL)
        ),
    CONSTRAINT ck_categories_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_categories_system_name_type
    ON categories (lower(name), transaction_type)
    WHERE user_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_categories_user_name_type
    ON categories (user_id, lower(name), transaction_type)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_categories_available
    ON categories (user_id, transaction_type, is_active)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_categories_updated_at
BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- TRANSACTIONS
-- amount is always positive. Direction is represented by transaction_type.
-- transaction_date and transaction_time are retained as requested.
-- =========================================================

CREATE TABLE transactions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    amount              numeric(19,4) NOT NULL,
    transaction_date    date NOT NULL DEFAULT CURRENT_DATE,
    transaction_time    time NOT NULL DEFAULT LOCALTIME,
    transaction_type    varchar(20) NOT NULL,
    user_id             uuid NOT NULL,
    account_id          uuid NOT NULL,
    category_id         uuid NOT NULL,
    description         text,
    note                text,
    source              varchar(30) NOT NULL DEFAULT 'MANUAL',
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          timestamptz,

    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT ck_transactions_amount
        CHECK (amount > 0),
    CONSTRAINT ck_transactions_type
        CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_source
        CHECK (source IN ('MANUAL', 'IMPORT', 'RECURRING', 'SYSTEM'))
);

CREATE INDEX ix_transactions_user_date
    ON transactions (user_id, transaction_date DESC, transaction_time DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_transactions_account_date
    ON transactions (account_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_transactions_category_date
    ON transactions (category_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_transactions_user_type_date
    ON transactions (user_id, transaction_type, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_transactions_updated_at
BEFORE UPDATE ON transactions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION validate_transaction_ownership()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_account_user_id uuid;
    v_account_active boolean;
    v_account_deleted_at timestamptz;
    v_category_user_id uuid;
    v_category_type varchar(20);
    v_category_active boolean;
    v_category_deleted_at timestamptz;
BEGIN
    SELECT user_id, is_active, deleted_at
      INTO v_account_user_id, v_account_active, v_account_deleted_at
      FROM accounts
     WHERE id = NEW.account_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Account % does not exist', NEW.account_id;
    END IF;

    IF v_account_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Transaction user must own the selected account';
    END IF;

    IF NOT v_account_active OR v_account_deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot post a transaction to an inactive or deleted account';
    END IF;

    SELECT user_id, transaction_type, is_active, deleted_at
      INTO v_category_user_id, v_category_type, v_category_active, v_category_deleted_at
      FROM categories
     WHERE id = NEW.category_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Category % does not exist', NEW.category_id;
    END IF;

    IF v_category_user_id IS NOT NULL AND v_category_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Transaction cannot use another user''s category';
    END IF;

    IF v_category_type <> NEW.transaction_type THEN
        RAISE EXCEPTION 'Category transaction type must match transaction type';
    END IF;

    IF NOT v_category_active OR v_category_deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot use an inactive or deleted category';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transactions_validate
BEFORE INSERT OR UPDATE OF user_id, account_id, category_id, transaction_type
ON transactions
FOR EACH ROW EXECUTE FUNCTION validate_transaction_ownership();

CREATE OR REPLACE FUNCTION maintain_account_balance()
RETURNS trigger
LANGUAGE plpgsql
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

CREATE TRIGGER trg_transactions_maintain_balance
AFTER INSERT OR UPDATE OR DELETE ON transactions
FOR EACH ROW EXECUTE FUNCTION maintain_account_balance();

-- =========================================================
-- BUDGETS
-- MVP budgets cover one complete calendar month.
-- period_type and rollover_enabled remain explicit for future migrations,
-- but are constrained to MONTHLY and false in the MVP schema.
-- =========================================================

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

-- =========================================================
-- NOTIFICATIONS
-- =========================================================

CREATE TABLE notifications (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL,
    title               varchar(200) NOT NULL,
    content             text NOT NULL,
    notification_type   varchar(30) NOT NULL,
    related_entity_type varchar(30),
    related_entity_id   uuid,
    is_read             boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at             timestamptz,

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_title_not_blank
        CHECK (btrim(title) <> ''),
    CONSTRAINT ck_notifications_content_not_blank
        CHECK (btrim(content) <> ''),
    CONSTRAINT ck_notifications_type
        CHECK (notification_type IN (
            'DAILY_REMINDER',
            'BUDGET_WARNING',
            'BUDGET_EXCEEDED',
            'SYSTEM'
        )),
    CONSTRAINT ck_notifications_read_state
        CHECK (
            (is_read = false AND read_at IS NULL)
            OR
            (is_read = true AND read_at IS NOT NULL)
        )
);

CREATE INDEX ix_notifications_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE is_read = false;

CREATE INDEX ix_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE OR REPLACE FUNCTION normalize_notification_read_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.is_read = true AND NEW.read_at IS NULL THEN
        NEW.read_at := CURRENT_TIMESTAMP;
    ELSIF NEW.is_read = false THEN
        NEW.read_at := NULL;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_read_state
BEFORE INSERT OR UPDATE OF is_read, read_at ON notifications
FOR EACH ROW EXECUTE FUNCTION normalize_notification_read_state();

-- =========================================================
-- NOTIFICATION SETTINGS
-- One settings row per user.
-- =========================================================

CREATE TABLE notification_settings (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     uuid NOT NULL UNIQUE,
    daily_reminder_enabled      boolean NOT NULL DEFAULT true,
    daily_reminder_time         time NOT NULL DEFAULT '20:00:00',
    budget_warning_enabled      boolean NOT NULL DEFAULT true,
    budget_exceeded_enabled     boolean NOT NULL DEFAULT true,
    system_notifications_enabled boolean NOT NULL DEFAULT true,
    in_app_enabled              boolean NOT NULL DEFAULT true,
    email_enabled               boolean NOT NULL DEFAULT false,
    push_enabled                boolean NOT NULL DEFAULT false,
    quiet_hours_enabled         boolean NOT NULL DEFAULT false,
    quiet_hours_start           time,
    quiet_hours_end             time,
    created_at                  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_settings_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_settings_quiet_hours
        CHECK (
            quiet_hours_enabled = false
            OR
            (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL)
        )
);

CREATE TRIGGER trg_notification_settings_updated_at
BEFORE UPDATE ON notification_settings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION create_default_notification_settings()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO notification_settings (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_create_notification_settings
AFTER INSERT ON users
FOR EACH ROW EXECUTE FUNCTION create_default_notification_settings();

-- =========================================================
-- REFRESH TOKENS
-- Store only a secure hash of the refresh token, never the raw token.
-- Token families support rotation and reuse detection.
-- =========================================================

CREATE TABLE refresh_tokens (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid NOT NULL,
    token_hash              varchar(255) NOT NULL,
    token_family_id         uuid NOT NULL DEFAULT gen_random_uuid(),
    expires_at              timestamptz NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at            timestamptz,
    revoked_at              timestamptz,
    replaced_by_token_id    uuid,
    revoke_reason           varchar(255),
    user_agent              text,
    ip_address              inet,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replacement
        FOREIGN KEY (replaced_by_token_id)
        REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    CONSTRAINT uq_refresh_tokens_hash
        UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_tokens_revocation
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX ix_refresh_tokens_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_refresh_tokens_family
    ON refresh_tokens (token_family_id);

CREATE INDEX ix_refresh_tokens_expiration
    ON refresh_tokens (expires_at);

-- =========================================================
-- USEFUL REPORTING VIEWS
-- =========================================================

CREATE VIEW account_balance_reconciliation AS
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

-- =========================================================
-- OPTIONAL DEFAULT CATEGORIES
-- =========================================================

INSERT INTO categories
    (name, transaction_type, icon, color, is_default, display_order)
VALUES
    ('Salary',          'INCOME',  'wallet',        '#2E7D32', true, 10),
    ('Business',        'INCOME',  'briefcase',     '#388E3C', true, 20),
    ('Investment',      'INCOME',  'trending-up',   '#43A047', true, 30),
    ('Interest',        'INCOME',  'percent',       '#66BB6A', true, 40),
    ('Food',            'EXPENSE', 'utensils',      '#EF5350', true, 10),
    ('Housing',         'EXPENSE', 'home',          '#AB47BC', true, 20),
    ('Utilities',       'EXPENSE', 'zap',           '#FFA726', true, 30),
    ('Transportation',  'EXPENSE', 'car',           '#42A5F5', true, 40),
    ('Entertainment',   'EXPENSE', 'film',          '#EC407A', true, 50),
    ('Healthcare',      'EXPENSE', 'heart-pulse',   '#26A69A', true, 60),
    ('Education',       'EXPENSE', 'graduation-cap','#5C6BC0', true, 70),
    ('Travel',          'EXPENSE', 'plane',         '#29B6F6', true, 80),
    ('Other Expense',   'EXPENSE', 'circle-ellipsis','#78909C', true, 999)
ON CONFLICT DO NOTHING;

COMMIT;

