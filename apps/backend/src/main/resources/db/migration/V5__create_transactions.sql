SET search_path TO finance, public;

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
SET search_path = finance, public
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

CREATE TRIGGER trg_transactions_maintain_balance
AFTER INSERT OR UPDATE OR DELETE ON transactions
FOR EACH ROW EXECUTE FUNCTION maintain_account_balance();

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
