ALTER TABLE app_users
    ADD COLUMN access_token_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_access_token_version_non_negative
        CHECK (access_token_version >= 0);
