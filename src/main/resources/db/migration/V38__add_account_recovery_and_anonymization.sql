ALTER TABLE app_user
    ADD COLUMN anonymized_at TIMESTAMP NULL;

CREATE INDEX idx_app_user_withdrawal_anonymization
    ON app_user (anonymized_at, deleted_at, id);

CREATE TABLE account_recovery_token (
    id BIGINT AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_account_recovery_token PRIMARY KEY (id),
    CONSTRAINT uk_account_recovery_token_user UNIQUE (user_id),
    CONSTRAINT uk_account_recovery_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_recovery_token_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_account_recovery_token_expiration
    ON account_recovery_token (expires_at, id);
