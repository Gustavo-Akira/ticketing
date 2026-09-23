CREATE TABLE user_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL CHECK (password_hash <> '')
);
CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX ix_refresh_sessions_user ON refresh_sessions(user_id);
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES refresh_sessions(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    consumed_at TIMESTAMPTZ
);
CREATE INDEX ix_refresh_tokens_session ON refresh_tokens(session_id);
