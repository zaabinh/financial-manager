SET search_path TO finance, public;

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
