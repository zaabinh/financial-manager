SET search_path TO finance, public;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified boolean;

UPDATE users
SET email_verified = false
WHERE email_verified IS NULL;

ALTER TABLE users
    ALTER COLUMN email_verified SET DEFAULT false,
    ALTER COLUMN email_verified SET NOT NULL;
