ALTER TABLE refresh_token ADD COLUMN session_id VARCHAR(36) NULL;
ALTER TABLE refresh_token ADD COLUMN absolute_expires_at TIMESTAMP NULL;

-- Existing refresh chains have no original login timestamp. Require re-login at this cutover;
-- retain the rows and all application/user data rather than inventing a session history.
UPDATE refresh_token
SET session_id = CONCAT('legacy-', id),
    absolute_expires_at = expires_at,
    revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP);

ALTER TABLE refresh_token MODIFY COLUMN session_id VARCHAR(36) NOT NULL;
ALTER TABLE refresh_token MODIFY COLUMN absolute_expires_at TIMESTAMP NOT NULL;
ALTER TABLE refresh_token ADD CONSTRAINT ck_refresh_token_absolute_expiration
    CHECK (expires_at <= absolute_expires_at);

CREATE INDEX idx_refresh_token_user_session ON refresh_token (user_id, session_id);
CREATE INDEX idx_refresh_token_absolute_expiration ON refresh_token (absolute_expires_at, id);
