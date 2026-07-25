CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hex do token opaco (nunca em claro)
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,                 -- NULL enquanto não usado (single-use)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
