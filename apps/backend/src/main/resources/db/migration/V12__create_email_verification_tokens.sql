SET search_path TO finance, public;

-- Existing accounts predate verification and must not be locked out on rollout.
UPDATE users
SET email_verified = true
WHERE email IS NOT NULL
  AND email_verified = false;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL,
    email       citext NOT NULL,
    token_hash  char(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at     timestamptz,
    revoked_at  timestamptz,

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verification_tokens_email_not_blank
        CHECK (btrim(email::text) <> ''),
    CONSTRAINT ck_email_verification_tokens_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_email_verification_tokens_used
        CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT ck_email_verification_tokens_revoked
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX IF NOT EXISTS ix_email_verification_tokens_user_active
    ON email_verification_tokens (user_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_email_verification_tokens_expiration
    ON email_verification_tokens (expires_at);
