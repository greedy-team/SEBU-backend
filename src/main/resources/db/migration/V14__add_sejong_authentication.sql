ALTER TABLE app_user
    ADD COLUMN provider VARCHAR(20) NULL;

ALTER TABLE app_user
    ADD COLUMN provider_user_id VARCHAR(100) NULL;

ALTER TABLE app_user
    ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_user
    MODIFY COLUMN email VARCHAR(255) NULL;

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_provider_identity UNIQUE (provider, provider_user_id);

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_provider CHECK (provider IS NULL OR provider IN ('SEJONG'));

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
