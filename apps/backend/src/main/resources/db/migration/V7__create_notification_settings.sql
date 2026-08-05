SET search_path TO finance, public;

CREATE TABLE notification_settings (
    id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                      uuid NOT NULL UNIQUE,
    daily_reminder_enabled       boolean NOT NULL DEFAULT true,
    daily_reminder_time          time NOT NULL DEFAULT '20:00:00',
    budget_warning_enabled       boolean NOT NULL DEFAULT true,
    budget_exceeded_enabled      boolean NOT NULL DEFAULT true,
    system_notifications_enabled boolean NOT NULL DEFAULT true,
    in_app_enabled               boolean NOT NULL DEFAULT true,
    email_enabled                boolean NOT NULL DEFAULT false,
    push_enabled                 boolean NOT NULL DEFAULT false,
    quiet_hours_enabled          boolean NOT NULL DEFAULT false,
    quiet_hours_start            time,
    quiet_hours_end              time,
    created_at                   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_settings_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_settings_quiet_hours
        CHECK (
            quiet_hours_enabled = false
            OR (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL)
        )
);

CREATE TRIGGER trg_notification_settings_updated_at
BEFORE UPDATE ON notification_settings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION create_default_notification_settings()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = finance, public
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

INSERT INTO notification_settings (user_id)
SELECT id
FROM users
ON CONFLICT (user_id) DO NOTHING;
