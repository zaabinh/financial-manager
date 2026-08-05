SET search_path TO finance, public;

-- current_balance is a database-maintained cache.
-- The transaction ledger remains authoritative.
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

CREATE TRIGGER trg_accounts_initialize_balance
BEFORE INSERT OR UPDATE OF initial_balance ON accounts
FOR EACH ROW EXECUTE FUNCTION accounts_initialize_balance();
