CREATE TABLE chonkit_sessions (
    -- Who the token belongs to.
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Contents of the refresh token.
    refresh_token TEXT PRIMARY KEY,

    -- The key name used to sign the token in the Vault.
    key_name TEXT NOT NULL,

    -- The version of the key used to sign the token in the Vault.
    key_version TEXT NOT NULL,

    -- Token expiration date time.
    expires_at TIMESTAMPTZ NOT NULL,

    -- Token creation date time.
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
