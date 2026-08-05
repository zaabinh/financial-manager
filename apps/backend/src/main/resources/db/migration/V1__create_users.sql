CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance, public;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = finance, public
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
SET search_path = finance, public
AS $$
    SELECT CASE
        WHEN p_transaction_type = 'INCOME' THEN p_amount
        WHEN p_transaction_type = 'EXPENSE' THEN -p_amount
        ELSE 0
    END;
$$;

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
            OR status <> 'DELETED'
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
