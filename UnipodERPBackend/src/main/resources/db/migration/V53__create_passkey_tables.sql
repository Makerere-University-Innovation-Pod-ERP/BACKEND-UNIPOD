CREATE TABLE passkey_credentials (
    credential_id VARCHAR(1024) NOT NULL,
    user_id UUID NOT NULL,
    public_key_cose BYTEA NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_passkey_credentials PRIMARY KEY (credential_id),
    CONSTRAINT fk_passkey_credentials_user FOREIGN KEY (user_id)
        REFERENCES api_users (id) ON DELETE CASCADE
);

CREATE INDEX ix_passkey_credentials_user_id ON passkey_credentials (user_id);

CREATE TABLE passkey_challenges (
    id UUID NOT NULL,
    user_id UUID,
    ceremony VARCHAR(16) NOT NULL,
    request_json TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_passkey_challenges PRIMARY KEY (id),
    CONSTRAINT fk_passkey_challenges_user FOREIGN KEY (user_id)
        REFERENCES api_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_passkey_challenges_ceremony CHECK (ceremony IN ('REGISTRATION', 'AUTHENTICATION'))
);

CREATE INDEX ix_passkey_challenges_expires_at ON passkey_challenges (expires_at);
