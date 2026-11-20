CREATE TABLE users (
    id bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    username text NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9_]{3,20}$'),
    display_name text NOT NULL CHECK (char_length(display_name) BETWEEN 2 AND 50),
    password_hash text NOT NULL CHECK (password_hash LIKE '$argon2id$%'),
    role text NOT NULL CHECK (role IN ('WORKSHOP', 'CLIENT')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX users_single_workshop ON users (role) WHERE role = 'WORKSHOP';

CREATE TABLE auth_sessions (
    token_hash bytea PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    user_id bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at)
);

CREATE INDEX auth_sessions_user_idx ON auth_sessions (user_id);
CREATE INDEX auth_sessions_expiry_idx ON auth_sessions (expires_at);

CREATE TABLE auth_rate_limits (
    bucket text NOT NULL CHECK (bucket IN ('login_ip', 'login_username_ip', 'register_ip')),
    identity_hash bytea NOT NULL CHECK (octet_length(identity_hash) = 32),
    attempts integer NOT NULL CHECK (attempts > 0),
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (bucket, identity_hash)
);

CREATE INDEX auth_rate_limits_expiry_idx ON auth_rate_limits (expires_at);
