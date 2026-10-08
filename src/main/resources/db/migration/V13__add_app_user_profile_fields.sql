ALTER TABLE app_user
    ADD (
        name VARCHAR(30) NULL,
        grade SMALLINT NULL,
        major_department_id BIGINT NULL,
        gpa_band VARCHAR(20) NULL,
        introduction VARCHAR(500) NOT NULL DEFAULT '',
        introduction_moderated_at TIMESTAMP NULL,
        introduction_policy_version VARCHAR(50) NULL,
        introduction_provider_version VARCHAR(100) NULL,
        profile_updated_at TIMESTAMP NULL,
        deleted_at TIMESTAMP NULL
    );

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_grade CHECK (
        grade IS NULL OR grade BETWEEN 1 AND 4
    );

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_gpa_band CHECK (
        gpa_band IS NULL
        OR gpa_band IN ('GTE_3_0', 'GTE_3_5', 'GTE_4_0')
    );

CREATE INDEX idx_app_user_major_department
    ON app_user (major_department_id);

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_major_department FOREIGN KEY (major_department_id)
        REFERENCES department (id) ON DELETE RESTRICT;

CREATE INDEX idx_app_user_deleted_at
    ON app_user (deleted_at);

CREATE INDEX idx_bookmark_user_created_at
    ON bookmark (user_id, created_at DESC, laboratory_id DESC);
