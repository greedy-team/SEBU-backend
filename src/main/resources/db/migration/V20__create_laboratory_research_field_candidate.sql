CREATE TABLE laboratory_research_field_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    laboratory_id BIGINT NOT NULL,
    source_field_key VARCHAR(64) NOT NULL,
    source_description_hash VARCHAR(64) NOT NULL,
    raw_field_text VARCHAR(2000) NOT NULL,
    candidate_name VARCHAR(100) NULL,
    extraction_method VARCHAR(30) NOT NULL,
    source_order INT NOT NULL,
    extraction_rule_version VARCHAR(30) NOT NULL,
    is_stale BOOLEAN NOT NULL DEFAULT FALSE,
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(1000) NULL,
    reviewed_by VARCHAR(100) NULL,
    reviewed_at TIMESTAMP NULL,
    review_revision BIGINT NOT NULL DEFAULT 0,
    extracted_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_laboratory_research_field_candidate PRIMARY KEY (id),
    CONSTRAINT uk_laboratory_research_field_candidate_identity
        UNIQUE (laboratory_id, source_field_key),
    CONSTRAINT fk_laboratory_research_field_candidate_laboratory
        FOREIGN KEY (laboratory_id)
        REFERENCES laboratory (id) ON DELETE CASCADE,
    CONSTRAINT ck_lrf_candidate_source_field_key CHECK (
        CHAR_LENGTH(source_field_key) = 64
    ),
    CONSTRAINT ck_lrf_candidate_description_hash CHECK (
        CHAR_LENGTH(source_description_hash) = 64
    ),
    CONSTRAINT ck_lrf_candidate_raw_field_text CHECK (
        TRIM(raw_field_text) <> ''
    ),
    CONSTRAINT ck_lrf_candidate_name CHECK (
        candidate_name IS NULL OR TRIM(candidate_name) <> ''
    ),
    CONSTRAINT ck_lrf_candidate_extraction_method CHECK (
        extraction_method IN ('WHOLE_TEXT', 'DELIMITED', 'LONG_TEXT')
    ),
    CONSTRAINT ck_lrf_candidate_source_order CHECK (
        source_order >= 0
    ),
    CONSTRAINT ck_lrf_candidate_rule_version CHECK (
        TRIM(extraction_rule_version) <> ''
    ),
    CONSTRAINT ck_lrf_candidate_review_status CHECK (
        review_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_lrf_candidate_review_metadata CHECK (
        (
            review_status = 'PENDING'
            AND reviewed_by IS NULL
            AND reviewed_at IS NULL
        )
        OR
        (
            review_status IN ('APPROVED', 'REJECTED')
            AND reviewed_by IS NOT NULL
            AND TRIM(reviewed_by) <> ''
            AND reviewed_at IS NOT NULL
            AND review_revision > 0
        )
    ),
    CONSTRAINT ck_lrf_candidate_approved_name CHECK (
        review_status <> 'APPROVED'
        OR candidate_name IS NOT NULL
    ),
    CONSTRAINT ck_lrf_candidate_review_revision CHECK (
        review_revision >= 0
    )
);

CREATE INDEX idx_lrf_candidate_current_review
    ON laboratory_research_field_candidate (is_stale, review_status, id);
