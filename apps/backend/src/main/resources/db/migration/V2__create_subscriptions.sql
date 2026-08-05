SET search_path TO finance, public;

-- Reserved for post-MVP entitlement and subscription capabilities.
CREATE TABLE subscription_plans (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(50) NOT NULL UNIQUE,
    name                varchar(100) NOT NULL,
    price               numeric(19,4) NOT NULL DEFAULT 0,
    currency            char(3) NOT NULL DEFAULT 'VND',
    billing_period      varchar(20) NOT NULL DEFAULT 'MONTHLY',
    max_accounts        integer,
    max_budgets         integer,
    features            jsonb NOT NULL DEFAULT '{}'::jsonb,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  uuid NOT NULL,
    plan_id                  uuid NOT NULL,
    status                   varchar(20) NOT NULL DEFAULT 'ACTIVE',
    start_at                 timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at                timestamptz,
    cancel_at_period_end     boolean NOT NULL DEFAULT false,
    cancelled_at             timestamptz,
    external_provider        varchar(50),
    external_subscription_id varchar(255),
    created_at               timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

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

INSERT INTO subscription_plans
    (code, name, price, currency, billing_period, max_accounts, max_budgets, features)
VALUES
    ('FREE', 'Free', 0, 'VND', 'FREE', 5, 10,
     '{"reports": true, "export": false, "multi_currency": false}'::jsonb)
ON CONFLICT (code) DO NOTHING;
