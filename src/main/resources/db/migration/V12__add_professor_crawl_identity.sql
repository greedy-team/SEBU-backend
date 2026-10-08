ALTER TABLE professor_crawl_candidate
    ADD COLUMN source_identity_key VARCHAR(320) NOT NULL DEFAULT '';

UPDATE professor_crawl_candidate
SET source_identity_key = CONCAT('legacy:', id);

ALTER TABLE professor_crawl_candidate
    ALTER COLUMN source_identity_key DROP DEFAULT;

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT ck_professor_crawl_candidate_identity_not_empty CHECK (
        source_identity_key <> ''
    );

ALTER TABLE professor_crawl_candidate
    ADD CONSTRAINT uk_professor_crawl_candidate_source_identity UNIQUE (
        source_id,
        source_identity_key
    );

ALTER TABLE professor_crawl_candidate
    DROP CONSTRAINT uk_professor_crawl_candidate_source_name;

CREATE INDEX idx_professor_crawl_candidate_source_name
    ON professor_crawl_candidate (source_id, professor_name);
