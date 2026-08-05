SET search_path TO finance, public;

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
            OR (is_default = false AND user_id IS NOT NULL)
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

INSERT INTO categories
    (name, transaction_type, icon, color, is_default, display_order)
VALUES
    ('Salary',          'INCOME',  'wallet',         '#2E7D32', true, 10),
    ('Business',        'INCOME',  'briefcase',      '#388E3C', true, 20),
    ('Investment',      'INCOME',  'trending-up',    '#43A047', true, 30),
    ('Interest',        'INCOME',  'percent',        '#66BB6A', true, 40),
    ('Food',            'EXPENSE', 'utensils',       '#EF5350', true, 10),
    ('Housing',         'EXPENSE', 'home',           '#AB47BC', true, 20),
    ('Utilities',       'EXPENSE', 'zap',            '#FFA726', true, 30),
    ('Transportation',  'EXPENSE', 'car',            '#42A5F5', true, 40),
    ('Entertainment',   'EXPENSE', 'film',           '#EC407A', true, 50),
    ('Healthcare',      'EXPENSE', 'heart-pulse',    '#26A69A', true, 60),
    ('Education',       'EXPENSE', 'graduation-cap', '#5C6BC0', true, 70),
    ('Travel',          'EXPENSE', 'plane',           '#29B6F6', true, 80),
    ('Other Expense',   'EXPENSE', 'circle-ellipsis','#78909C', true, 999)
ON CONFLICT DO NOTHING;
