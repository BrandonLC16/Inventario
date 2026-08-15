CREATE TABLE authentication_rate_limit_buckets (
    bucket_key VARCHAR(160) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count BIGINT NOT NULL,
    CONSTRAINT ck_auth_rate_limit_attempt_count_positive
        CHECK (attempt_count > 0),
    CONSTRAINT ck_auth_rate_limit_window_valid
        CHECK (expires_at > window_started_at)
);

CREATE INDEX idx_auth_rate_limit_buckets_expiry
    ON authentication_rate_limit_buckets (expires_at);
