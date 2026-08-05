SET search_path TO finance, public;

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
            OR (is_read = true AND read_at IS NOT NULL)
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
SET search_path = finance, public
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
